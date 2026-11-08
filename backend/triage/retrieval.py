"""Read-only request-time vector retrieval. Only curated public references are stored."""
import asyncio
import hashlib
import json
import math
import os
from pathlib import Path
import sqlite3
import tempfile
from contextlib import contextmanager

from google.genai import types
import sqlite_vec

ROOT = Path(__file__).resolve().parent
CORPUS = ROOT / 'references.json'
INDEX = ROOT / 'references.sqlite3'
MODEL = 'gemini-embedding-001'
DIMENSIONS = 768
# Starting relevance cutoff, not a calibrated fraud score.
MAX_DISTANCE = 0.40


def corpus():
    raw = CORPUS.read_bytes()
    docs = json.loads(raw)
    ids = [doc['id'] for doc in docs]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate reference IDs')
    for doc in docs:
        if not doc['url'].startswith(('https://rbi.org.in/', 'https://www.rbi.org.in/')):
            raise ValueError('Unreviewed reference source')
    return docs, hashlib.sha256(raw).hexdigest()


@contextmanager
def connect(path, readonly=False):
    connection = sqlite3.connect(path.as_uri() + '?mode=ro' if readonly else str(path), uri=readonly)
    connection.enable_load_extension(True)
    sqlite_vec.load(connection)
    connection.enable_load_extension(False)
    try:
        yield connection
        if not readonly:
            connection.commit()
    finally:
        connection.close()


def vector(values):
    if len(values) != DIMENSIONS or not all(math.isfinite(v) for v in values) or not any(values):
        raise ValueError('Invalid embedding')
    return sqlite_vec.serialize_float32(values)


async def embed(text, task):
    # Import here to avoid a cycle with the generation service.
    from .gemini_service import get_client
    async with get_client().aio as client:
        response = await client.models.embed_content(model=MODEL, contents=text,
            config=types.EmbedContentConfig(task_type=task, output_dimensionality=DIMENSIONS))
    if not response.embeddings or not response.embeddings[0].values:
        raise ValueError('No embedding returned')
    return response.embeddings[0].values


def write_index(path, docs, vectors, digest):
    """Build separately and atomically replace; a failed rebuild retains the old DB."""
    if len(docs) != len(vectors):
        raise ValueError('Missing embeddings')
    handle, filename = tempfile.mkstemp(prefix='reference-build-', suffix='.sqlite3', dir=path.parent)
    os.close(handle)
    temp = Path(filename)
    try:
        with connect(temp) as db:
            db.execute('CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
            db.executemany('INSERT INTO metadata VALUES (?, ?)', [('model', MODEL), ('dimensions', str(DIMENSIONS)), ('corpus_sha256', digest)])
            db.execute('CREATE TABLE documents (rowid INTEGER PRIMARY KEY, document TEXT NOT NULL)')
            db.execute(f'CREATE VIRTUAL TABLE vectors USING vec0(embedding float[{DIMENSIONS}] distance_metric=cosine)')
            for row, (doc, values) in enumerate(zip(docs, vectors), 1):
                db.execute('INSERT INTO documents VALUES (?, ?)', (row, json.dumps(doc, ensure_ascii=False)))
                db.execute('INSERT INTO vectors(rowid, embedding) VALUES (?, ?)', (row, vector(values)))
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


async def build_index():
    docs, digest = corpus()
    vectors = []
    for doc in docs:
        async with asyncio.timeout(45):
            vectors.append(await embed(doc['title'] + '\n' + doc['summary'], 'RETRIEVAL_DOCUMENT'))
    await asyncio.to_thread(write_index, INDEX, docs, vectors, digest)
    return {'documents': len(docs), 'model': MODEL, 'dimensions': DIMENSIONS, 'corpus_sha256': digest}


def index_ready(path=INDEX):
    _, digest = corpus()
    with connect(path, readonly=True) as db:
        meta = dict(db.execute('SELECT key, value FROM metadata'))
        if meta != {'model': MODEL, 'dimensions': str(DIMENSIONS), 'corpus_sha256': digest}:
            raise ValueError('Reference index is stale; rebuild it')


def search(values, path=INDEX):
    index_ready(path)
    with connect(path, readonly=True) as db:
        rows = db.execute('SELECT rowid, distance FROM vectors WHERE embedding MATCH ? AND k = 3 ORDER BY distance', (vector(values),)).fetchall()
        cutoff = min(MAX_DISTANCE, rows[0][1] + 0.05) if rows else MAX_DISTANCE
        return [json.loads(db.execute('SELECT document FROM documents WHERE rowid = ?', (row,)).fetchone()[0])
                for row, distance in rows if distance <= cutoff]


async def retrieve(text):
    if not text.strip():
        return [], 'not_requested'
    try:
        # Check locally before paying for an embedding. Never write the query/vector.
        await asyncio.to_thread(index_ready)
        async with asyncio.timeout(12):
            values = await embed(text[-6000:], 'RETRIEVAL_QUERY')
            docs = await asyncio.to_thread(search, values)
        return docs, 'matched' if docs else 'no_match'
    except Exception:
        # Missing/corrupt index, upstream timeout or unavailable embedding: honest fallback.
        return [], 'unavailable'
