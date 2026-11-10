// Fill the official 16:9 PDF template while preserving its organizer branding.
// Run from Dhalam-Hack2Skill: swift -module-cache-path /private/tmp/dhalam-swift-cache tools/build_submission_deck.swift
import Foundation
import CoreGraphics
import CoreText
import ImageIO

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let templateURL = root.appendingPathComponent("docs/submission/Startup Buildathon _ Idea Submission Deck.pdf")
let outputURL = root.appendingPathComponent("docs/submission/Dhalam-AI-Idea-Submission.pdf")
let logoURL = root.appendingPathComponent("app/src/main/res/drawable/dhalam_logo_transparent.png")
guard let source = CGPDFDocument(templateURL as CFURL), source.numberOfPages == 9 else {
    fatalError("Expected the nine-page official template")
}
var pageBox = CGRect(x: 0, y: 0, width: 720, height: 405)
guard let pdf = CGContext(outputURL as CFURL, mediaBox: &pageBox, nil) else {
    fatalError("Could not create submission PDF")
}

struct Ink {
    let r: CGFloat, g: CGFloat, b: CGFloat
    var cg: CGColor { CGColor(colorSpace: CGColorSpaceCreateDeviceRGB(), components: [r, g, b, 1])! }
}
let navy = Ink(r: 0.08, g: 0.15, b: 0.23)
let teal = Ink(r: 0.00, g: 0.58, b: 0.60)
let pale = Ink(r: 0.91, g: 0.97, b: 0.97)
let gray = Ink(r: 0.31, g: 0.37, b: 0.43)
let border = Ink(r: 0.85, g: 0.89, b: 0.91)
let white = Ink(r: 1, g: 1, b: 1)

func rect(_ x: CGFloat, _ top: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGRect {
    CGRect(x: x, y: 405 - top - h, width: w, height: h)
}

func fill(_ r: CGRect, _ color: Ink) {
    pdf.setFillColor(color.cg)
    pdf.fill(r)
}

func card(_ x: CGFloat, _ top: CGFloat, _ w: CGFloat, _ h: CGFloat, tint: Ink = white) {
    let path = CGPath(roundedRect: rect(x, top, w, h), cornerWidth: 10, cornerHeight: 10, transform: nil)
    pdf.addPath(path)
    pdf.setFillColor(tint.cg)
    pdf.fillPath()
    pdf.addPath(path)
    pdf.setStrokeColor(border.cg)
    pdf.setLineWidth(0.7)
    pdf.strokePath()
}

func label(_ s: String, _ x: CGFloat, _ top: CGFloat, _ w: CGFloat, _ h: CGFloat,
           size: CGFloat = 13, bold: Bool = false, color: Ink = navy) {
    let font = CTFontCreateWithName((bold ? "HelveticaNeue-Bold" : "HelveticaNeue") as CFString, size, nil)
    let attrs: [NSAttributedString.Key: Any] = [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color.cg
    ]
    let string = NSAttributedString(string: s, attributes: attrs)
    let framesetter = CTFramesetterCreateWithAttributedString(string)
    let frame = CTFramesetterCreateFrame(framesetter, CFRange(location: 0, length: string.length),
                                       CGPath(rect: rect(x, top, w, h), transform: nil), nil)
    CTFrameDraw(frame, pdf)
}

func slideTitle(_ title: String, _ subtitle: String) {
    // Keep the official logo row and replace the blank instructional content.
    fill(rect(0, 67, 720, 338), white)
    label(title, 54, 79, 612, 34, size: 23, bold: true)
    label(subtitle, 54, 111, 612, 24, size: 11.5, color: gray)
    fill(rect(54, 385, 612, 1.2), teal)
}

func footer(_ s: String) {
    label(s, 54, 390, 612, 11, size: 7.5, color: gray)
}

func drawLogo(_ x: CGFloat, _ top: CGFloat, _ side: CGFloat) {
    guard let imageSource = CGImageSourceCreateWithURL(logoURL as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(imageSource, 0, nil) else { return }
    pdf.draw(image, in: rect(x, top, side, side))
}

func drawImage(_ path: String, _ x: CGFloat, _ top: CGFloat, _ w: CGFloat, _ h: CGFloat) {
    let url = URL(fileURLWithPath: path)
    guard let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(imageSource, 0, nil) else { return }
    pdf.draw(image, in: rect(x, top, w, h))
}

func templateBackground(_ page: CGPDFPage) -> CGImage {
    // Rasterize only the branded template background. This keeps the organizers'
    // artwork while removing covered "Type here" text from PDF text extraction.
    let scale = 2
    guard let bitmap = CGContext(data: nil, width: 720 * scale, height: 405 * scale,
                                 bitsPerComponent: 8, bytesPerRow: 0,
                                 space: CGColorSpaceCreateDeviceRGB(),
                                 bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
        fatalError("Could not rasterize template page")
    }
    bitmap.setFillColor(white.cg)
    bitmap.fill(CGRect(x: 0, y: 0, width: 720 * scale, height: 405 * scale))
    bitmap.scaleBy(x: CGFloat(scale), y: CGFloat(scale))
    bitmap.drawPDFPage(page)
    guard let image = bitmap.makeImage() else { fatalError("Could not create template image") }
    return image
}

for templatePage in 2...9 {
    guard let page = source.page(at: templatePage) else { fatalError("Missing template page") }
    pdf.beginPDFPage(nil)
    pdf.draw(templateBackground(page), in: pageBox)
    switch templatePage {
    case 2:
        fill(rect(0, 215, 720, 190), white)
        label("Dhalam AI — Real-Time Coercion & Sideloading Defense", 54, 225, 510, 28, size: 19, bold: true)
        label("Team Ladydebuggers (2 Members)  |  Pitch Deck", 54, 255, 500, 18, size: 11, bold: true, color: teal)
        label("Anusha Jain (Systems Architect, AI Engineer at IBM) & Abhyudaya Marya (Product Strategy, Genpact)", 54, 273, 510, 16, size: 9.5, color: gray)
        fill(rect(54, 298, 6, 80), teal)
        label("The Problem: India's ₹1,750+ Cr Social Engineering & Coercion Crisis", 72, 297, 465, 18, size: 11.5, bold: true, color: teal)
        label("Scammers exploit psychological duress (fake Police 'Digital Arrest') to force victims to install legitimate remote-access utilities (AnyDesk) and drain accounts. Antivirus checks file signatures and fails; LLM chatbots are blind to device state. Panicked victims have no way to verify threats or device safety.", 72, 318, 485, 60, size: 10)
        drawLogo(576, 256, 98)
    case 3:
        slideTitle("The Crisis of Digital Arrest & Social Engineering in India", "A person under pressure needs a combined assessment of conversational context and device safety.")
        let steps: [(String, String)] = [
            ("1  Psychological Duress", "Fraudsters forge official CBI warrants or fake bill cutoffs to create panic."),
            ("2  Weaponized Utilities", "Victims are directed to install remote support tools or grant Accessibility access."),
            ("3  The Systems Disconnect", "Antivirus misses legitimate tools; chatbots analyze text in a vacuum without device context.")
        ]
        for (i, step) in steps.enumerated() {
            let top = CGFloat(145 + i * 70)
            card(54, top, 365, 60)
            label(step.0, 68, top + 8, 330, 17, size: 12.5, bold: true, color: teal)
            label(step.1, 68, top + 27, 328, 28, size: 10.2, color: gray)
        }
        card(437, 145, 229, 200, tint: pale)
        label("The user's question", 453, 161, 197, 25, size: 15, bold: true)
        label("Is this notice genuine?\nIs my screen recorded?\nHow do I secure this?", 453, 199, 192, 96, size: 16)
        label("Dhalam helps the person pause, correlate clues, and revoke dangerous access.", 453, 302, 194, 40, size: 10.5, color: gray)
        footer("Problem framing: Social engineering and device-access hijacking; blind spots in traditional antivirus.")
    case 4:
        slideTitle("Dhalam AI — Interlocked, Closed-Loop Coercion Defense", "Consent before analysis. Explain the evidence. Correlate with telemetry. Guide remediation.")
        card(54, 146, 270, 230, tint: pale)
        label("Proposed Solution Workflow", 68, 158, 242, 20, size: 12.5, bold: true, color: teal)
        label("• 01  Multimodal Triage\nIngests screenshots & text. Gemini 3.8 Flash strips EXIF GPS & triages coercion risk.\n\n• 02  Correlate Telemetry\nApp audits active screen recording and enabled Accessibility tools in real time.\n\n• 03  Grounded Advice\nsqlite-vec retrieves official RBI circulars with strict citation checks.\n\n• 04  Remediate & Verify\nSettings deep-links launch deactivation; re-scan confirms device is secure.", 68, 182, 242, 184, size: 8.8, color: gray)
        
        let appImg = "/Users/anushajain/Desktop/2026-pilots/google-startup-hackathon/Dhalam-Hack2Skill/docs/interact-with-app/Screenshot_1794325662.png"
        drawImage(appImg, 360, 146, 120, 213)
        label("Native Compose App", 360, 365, 120, 15, size: 9, bold: true, color: teal)
        
        let botImg = "/Users/anushajain/Desktop/2026-pilots/google-startup-hackathon/Dhalam-Hack2Skill/docs/interact-with-app/Screenshot_1794326037.png"
        drawImage(botImg, 510, 146, 120, 213)
        label("WhatsApp Triage Bot", 510, 365, 120, 15, size: 9, bold: true, color: teal)
        
        footer("Security boundaries: Privacy compliance, size-bounded payloads, zero raw screenshot persistence.")
    case 5:
        slideTitle("Systems Architecture: Interlocking AI & OS Telemetry", "Clients expose only the device observations their operating system permits.")
        let cols: [(CGFloat, String, String)] = [
            (54, "CLIENT FRONTENDS", "Native Android Compose App\nMeta WhatsApp Bot\n\nFriction-free Credential Manager Google Sign-In, Live accessibility status, and screen capture detection."),
            (266, "SERVERLESS CLOUD RUN", "FastAPI in Mumbai (asia-south1)\nCryptographic Google ID token verification\nSliding-window rate limits\n\nPillow EXIF metadata sanitizer"),
            (478, "AI & VECTOR GROUNDING", "Vertex AI Gemini 3.8 Flash\nStructured JSON schema\ngemini-embedding-001 (768-dim)\n\nIn-process sqlite-vec vector DB with strict citation matching")
        ]
        for (x, title, body) in cols {
            card(x, 147, 188, 168)
            label(title, x + 13, 160, 162, 22, size: 12.8, bold: true, color: teal)
            label(body, x + 13, 192, 160, 112, size: 10.7)
        }
        card(54, 328, 612, 43, tint: pale)
        label("Enterprise Security & Privacy", 68, 336, 175, 19, size: 11, bold: true, color: teal)
        label("Uploaded images are stripped of GPS/EXIF metadata before AI dispatch. No screenshots are persisted on Cloud Run or Vertex AI. Size-bounded, consent-gated, and rate-limited API design.", 250, 335, 400, 27, size: 10.3)
        footer("Native Android accesses AccessibilityManager capabilities and screen capture callbacks; iOS is restricted.")
    case 6:
        slideTitle("Google AI & Cloud Platform Core Integration", "Complete architectural alignment across Google's modern developer ecosystem.")
        let tools: [(CGFloat, CGFloat, String, String)] = [
            (54, 147, "Gemini on Vertex AI", "Structured multimodal scam assessments using strict Pydantic ModelAssessment schema enforcement."),
            (366, 147, "gemini-embedding-001", "Generates 768-dim embeddings powering in-process sqlite-vec vector RAG with $0 DB cluster cost."),
            (54, 249, "Google Cloud Run", "Hosted containerized API in Mumbai (asia-south1) for local data residency and ultra-low latency."),
            (366, 249, "Android Credential Manager", "Frictionless Google OAuth 2.0. Plus Google Safe Browsing API v4, and Google Noto Sans Indic typography.")
        ]
        for (x, top, title, body) in tools {
            card(x, top, 300, 91, tint: title.contains("Manager") ? pale : white)
            label(title, x + 14, top + 12, 270, 22, size: 13.1, bold: true, color: teal)
            label(body, x + 14, top + 40, 269, 43, size: 10.7)
        }
        footer("Ecosystem showcase: Google DeepMind & Google Cloud. Agentic tool-calling skills planned for Buildathon.")
    case 7:
        slideTitle("Two Google AI Models, Two Specialized Roles", "Structured multimodal reasoning and semantic vector indexing working in harmony.")
        card(54, 146, 300, 194)
        label("Gemini (Vertex AI)", 70, 162, 268, 26, size: 17, bold: true, color: teal)
        label("Input: Consented text or screenshots.\n\nOutput: Strict Pydantic JSON schema mapping coercion tactics, threat severity, and physical safety actions.\n\nReasoning: Evaluated with synthetic and user-consented examples across 6 Indic languages.", 70, 202, 265, 130, size: 11.2)
        card(366, 146, 300, 194, tint: pale)
        label("gemini-embedding-001", 382, 162, 266, 28, size: 16, bold: true, color: teal)
        label("Role: Embeds queries and the official RBI regulatory advisory corpus for vector retrieval.\n\nMoat: Powering the sqlite-vec index running in-process on Cloud Run with $0 database cost.\n\nGrounding: Programmatic citation validation blocks LLM hallucinations.", 382, 203, 264, 126, size: 11.2)
        label("AI acts as an intelligent assistant to human verification, not an automated safety arbiter or transaction blocker.", 54, 353, 612, 20, size: 10.8, bold: true)
        footer("AI evaluation parameters: False reassurance, unsupported claims, multilingual clarity, and user comprehension.")
    case 8:
        slideTitle("Market Viability & Go-To-Market Business Model", "High commercial value solving a critical digital banking liability for financial institutions.")
        card(54, 145, 295, 170)
        label("TAM / SAM & Acquisition", 69, 160, 262, 22, size: 13.5, bold: true, color: teal)
        label("TAM: ₹3,600 Cr ($430M) covering 350M+ UPI users in India. SAM: ₹750 Cr ($90M) vulnerable groups and RBI-regulated banks.\n\nConsumer subscriptions are unviable; free app & WhatsApp bot drive organic brand trust and top-of-funnel.", 69, 194, 258, 107, size: 10.9)
        card(370, 145, 296, 170, tint: pale)
        label("B2B2C Embedded Banking SDK", 385, 160, 265, 22, size: 13.5, bold: true, color: teal)
        label("Embedded SDK for Indian commercial banks (HDFC, SBI, ICICI) and UPI payment apps (PhonePe, Google Pay).\n\nMoat: Telemetry checks run before authorizing transfers > ₹10,000, reducing bank fraud liability. SaaS license at $0.02 / MAU or micro-fee.", 385, 194, 265, 103, size: 10.9)
        card(54, 327, 612, 45)
        label("Execution Proof & Unit Economics", 69, 337, 250, 18, size: 11.5, bold: true, color: teal)
        label("Gemini Flash check costs ~7 paise ($0.0008); in-process vector DB runs at zero cost, yielding >85% gross margins. Live deployment running, backed by 110+ JVM, Flutter, and backend tests.", 325, 337, 330, 28, size: 10.7)
        footer("Competitive differentiation: Interlocked context & telemetry correlation outperforms standalone scanners.")
    case 9:
        fill(rect(0, 240, 470, 165), white)
        label("Dhalam AI", 54, 255, 405, 40, size: 32, bold: true)
        label("Turn a pressured moment into a verified, protected decision.", 54, 302, 402, 38, size: 13.5, color: teal)
        label("Ladydebuggers  •  Anusha Jain & Abhyudaya Marya", 54, 345, 402, 16, size: 11, bold: true, color: navy)
        label("Live Endpoint: https://dhalam-api-xxpghctsqq-el.a.run.app/health", 54, 368, 402, 16, size: 9.5, color: gray)
    default: break
    }
    pdf.endPDFPage()
}
pdf.closePDF()
print(outputURL.path)
