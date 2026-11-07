import os
from pathlib import Path
from dotenv import load_dotenv

# Local development only; deployments receive configuration from Cloud Run.
if not os.getenv('K_SERVICE'):
    for path in (Path(__file__).resolve().parents[1] / '.env',):
        if path.exists():
            load_dotenv(path)
            break


class Settings:
    GOOGLE_CLOUD_PROJECT = os.getenv('GOOGLE_CLOUD_PROJECT', 'project-075ab0b6-c39b-4175-9b1')
    GOOGLE_CLOUD_LOCATION = os.getenv('GOOGLE_CLOUD_LOCATION', 'global')
    DEFAULT_MODEL = os.getenv('GEMINI_MODEL', 'gemini-3.8-flash')
    # An old API key in .env must not silently override Vertex/ADC.
    USE_VERTEX = os.getenv('GOOGLE_GENAI_USE_VERTEXAI', 'true').lower() == 'true'
    GEMINI_API_KEY = os.getenv('GEMINI_API_KEY')
    SAFE_BROWSING_API_KEY = os.getenv('SAFE_BROWSING_API_KEY')


settings = Settings()
