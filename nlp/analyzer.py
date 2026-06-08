import re
import os
from pathlib import Path
from typing import List, Tuple

import yake
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
import joblib

ITALIAN_STOP_WORDS = [
    "a", "ad", "al", "alla", "allo", "ai", "agli", "alle",
    "an", "ancora", "anche", "avere", "aveva", "avevano",
    "ben", "buon",
    "che", "chi", "ci", "coi", "col", "come", "con", "contro", "cor",
    "cui",
    "da", "dagli", "dai", "dal", "dalla", "dallo", "delle", "degli",
    "dei", "del", "dell", "della", "dello", "di", "doppi", "dov", "dove",
    "e", "è", "entrambi", "entro",
    "fra",
    "già", "gli",
    "ha", "hai", "hanno", "ho",
    "il", "in", "infine", "io", "la", "le", "lo", "lui",
    "ma", "mai", "me", "mentre", "mi", "mia", "mie", "miei", "mio",
    "molti", "molto",
    "ne", "nei", "nel", "nell", "nella", "nello", "noi", "no", "non",
    "nostra", "nostre", "nostri", "nostro",
    "o", "ora", "oltre",
    "per", "perché", "più", "poi", "può",
    "qua", "qual", "qualche", "quali", "quando", "quanto", "quasi",
    "quegli", "quei", "quel", "quell", "quella", "quelle", "quelli",
    "quello", "questi", "questo", "qui",
    "se", "sé", "senza", "sia", "siamo", "siete", "sono", "si", "solo",
    "sopra", "sotto", "sta", "stai", "stando", "stanno", "state",
    "stato", "stava", "stavano", "stavi", "stavo", "ste", "stia",
    "stiamo", "stiano", "stiate", "sto", "su", "sua", "sue", "sui",
    "sul", "sull", "sulla", "sullo", "suo", "suoi",
    "te", "ti", "tra", "troppo", "tu", "tua", "tue", "tuo", "tuoi",
    "tutti", "tutto",
    "un", "una", "uno", "uomo",
    "va", "vi", "vicino", "voi", "vostra", "vostre", "vostri", "vostro",
]

CATEGORIES = ["rete", "database", "bug-applicativo", "configurazione", "hardware", "servizi-web", "altro"]
PRIORITIES = ["p1", "p2", "p3", "p4"]

MODEL_DIR = Path(os.getenv("MODEL_DIR", "/app/models"))
MODEL_DIR.mkdir(parents=True, exist_ok=True)
MODEL_PATH = MODEL_DIR / "classifier.joblib"

kw_extractor = yake.KeywordExtractor(lan="it", n=3, dedupLim=0.9, top=10)

CATEGORY_KEYWORDS = {
    "rete": [
        "rete", "vpn", "connessione", "dns", "router", "firewall", "proxy",
        "cablaggio", "switch", "wi-fi", "lan", "wan", "ping", "timeout",
        "latenza", "banda", "tcp", "ip", "dhcp", "nat", "gateway",
        "access point", "connettività",
    ],
    "database": [
        "database", "db", "sql", "query", "mysql", "postgresql",
        "oracle", "mongodb", "tabella", "indice", "trigger",
        "procedura", "vista", "transazione", "lock", "deadlock",
        "connessione db", "errore database", "dati", "migrazione dati",
        "backup", "restore", "select", "insert", "update", "delete",
        "schema", "catalogo",
    ],
    "bug-applicativo": [
        "bug", "errore", "eccezione", "crash", "stacktrace",
        "null pointer", "segmentation fault", "non funziona",
        "malfunzionamento", "anomalia", "errore software",
        "eccezione", "fallimento", "pianterello", "si blocca",
        "non risponde", "freeze", "eccezione",
    ],
    "configurazione": [
        "configurazione", "config", "impostazione", "settaggio",
        "parametro", "proprietà", "variabile d'ambiente",
        "setup", "installazione", "deploy", "aggiornamento",
        "upgrade", "migrazione", "ambiente", "server",
        "avvio", "start", "restart", "config file",
    ],
    "hardware": [
        "hardware", "stampante", "monitor", "schermo", "tastiera",
        "mouse", "disco", "ssd", "hdd", "ram", "cpu", "scheda",
        "alimentatore", "periferica", "rotto", "guasto",
        "surriscaldamento", "ventola", "componente", "fisico",
        "server spento", "hard disk",
    ],
    "servizi-web": [
        "web", "api", "endpoint", "http", "https", "rest", "soap",
        "servizio web", "certificato", "ssl", "tls", "dominio",
        "url", "cloud", "saas", "portale", "sito", "502", "503",
        "servizio online", "gateway",
    ],
}

PRIORITY_KEYWORDS = {
    "p1": [
        "critico", "bloccante", "produzione ferma", "down",
        "non accessibile", "immediato", "fermo", "bloccato",
        "grave", "disastro", "emergenza",
    ],
    "p2": [
        "alto", "importante", "urgente", "serio", "significativo",
        "priorità", "considerevole",
    ],
    "p3": [
        "medio", "normale", "moderato", "standard", "regolare",
    ],
    "p4": [
        "basso", "minore", "cosmetico", "trascurabile",
        "miglioramento", "richiesta",
    ],
}

SEED_TRAINING_DATA: List[Tuple[str, str]] = [
    ("La connessione VPN non funziona più da questa mattina", "rete"),
    ("Il router in ufficio si riavvia continuamente", "rete"),
    ("Problemi di latenza sulla rete LAN", "rete"),
    ("Non riesco a connettermi al server tramite SSH", "rete"),
    ("La rete Wi-Fi non copre tutto l'ufficio", "rete"),
    ("Ping verso il gateway principale supera i 500ms", "rete"),
    ("Il firewall blocca le connessioni in uscita sulla porta 443", "rete"),
    ("Rete instabile dopo l'aggiornamento del firmware dello switch", "rete"),

    ("La query SQL va in timeout dopo 30 secondi", "database"),
    ("Il database PostgreSQL non risponde più", "database"),
    ("Errore di connessione al database in produzione", "database"),
    ("Le tabelle del database si sono corrotte", "database"),
    ("Problemi di locking sulle transazioni", "database"),
    ("La replica del database è in ritardo di 10 minuti", "database"),
    ("Errore ORA-00001: unique constraint violato su tabella utenti", "database"),
    ("La query di ricerca impiega più di un minuto per restituire risultati", "database"),

    ("L'applicazione crasha quando carico un file", "bug-applicativo"),
    ("Errore NullPointerException nella homepage", "bug-applicativo"),
    ("Il pulsante di salvataggio non funziona", "bug-applicativo"),
    ("Stacktrace quando apro la schermata di report", "bug-applicativo"),
    ("L'applicazione non risponde più dopo l'aggiornamento", "bug-applicativo"),
    ("Il form di login non valida correttamente le credenziali", "bug-applicativo"),
    ("Errore 500 all'apertura della pagina dei dettagli ordine", "bug-applicativo"),
    ("I dati mostrati nella dashboard non sono aggiornati", "bug-applicativo"),

    ("Devo aggiornare i parametri di configurazione del server", "configurazione"),
    ("Impostare le variabili d'ambiente per il nuovo deploy", "configurazione"),
    ("Configurare il proxy per l'accesso a internet", "configurazione"),
    ("Modificare le proprietà dell'applicazione in produzione", "configurazione"),
    ("Installazione e setup del nuovo ambiente di sviluppo", "configurazione"),
    ("Configurazione del certificato SSL per il dominio", "configurazione"),
    ("Parametrizzare l'URL del database nel file di properties", "configurazione"),
    ("Aggiungere il nuovo endpoint alla configurazione del load balancer", "configurazione"),

    ("La stampante non stampa più", "hardware"),
    ("Il monitor rimane nero all'accensione", "hardware"),
    ("Il disco SSD non viene rilevato dal sistema", "hardware"),
    ("La ventola del server fa molto rumore", "hardware"),
    ("La tastiera non funziona dopo l'ultimo aggiornamento", "hardware"),
    ("Il computer si surriscalda e si spegne", "hardware"),
    ("Il mouse si disconnette continuamente", "hardware"),
    ("L'alimentatore del PC ha smesso di funzionare", "hardware"),

    ("Il sito web non risponde sulla porta 443", "servizi-web"),
    ("Il certificato SSL è scaduto e blocca l'accesso", "servizi-web"),
    ("L'API REST non restituisce risultati", "servizi-web"),
    ("L'endpoint di autenticazione va in timeout", "servizi-web"),
    ("Il servizio web è irraggiungibile dall'esterno", "servizi-web"),
    ("Il portale aziendale mostra errore 502", "servizi-web"),
    ("Le chiamate API sono lentissime", "servizi-web"),
    ("Il certificato TLS non è valido per il dominio", "servizi-web"),

    ("Richiesta di informazioni sul progetto", "altro"),
    ("Documentazione da aggiornare", "altro"),
    ("Nuovo utente da registrare al sistema", "altro"),
    ("Domanda sulle policy aziendali di sicurezza", "altro"),
    ("Richiesta di accesso al repository Git", "altro"),
    ("Formazione necessaria per il nuovo software", "altro"),
    ("Richiesta di ferie e permessi", "altro"),
]


def _preprocess(text: str) -> str:
    text = text.lower()
    text = re.sub(r"[^a-zàèéìíîòóùúçë0-9\s]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _train_model():
    texts = [t for t, _ in SEED_TRAINING_DATA]
    labels = [l for _, l in SEED_TRAINING_DATA]

    pipeline = Pipeline([
        ("tfidf", TfidfVectorizer(
            max_features=5000,
            ngram_range=(1, 2),
            preprocessor=_preprocess,
            stop_words=ITALIAN_STOP_WORDS,
        )),
        ("clf", LogisticRegression(max_iter=1000, multi_class="multinomial")),
    ])
    pipeline.fit(texts, labels)
    joblib.dump(pipeline, MODEL_PATH)
    return pipeline


def _load_model():
    if MODEL_PATH.exists():
        return joblib.load(MODEL_PATH)
    return _train_model()


def extract_keywords(text: str, top_n: int = 10) -> List[str]:
    preprocessed = _preprocess(text)
    if not preprocessed.strip():
        return []
    keywords = kw_extractor.extract_keywords(preprocessed)
    keywords.sort(key=lambda x: x[1], reverse=False)
    result = []
    seen = set()
    for kw, score in keywords:
        normalized = kw.lower().strip()
        if normalized not in seen and normalized:
            result.append(normalized)
            seen.add(normalized)
        if len(result) >= top_n:
            break
    return result if result else []


_keyword_model = _load_model()


def classify_category(text: str) -> Tuple[str, float]:
    preprocessed = _preprocess(text)

    try:
        probs = _keyword_model.predict_proba([preprocessed])[0]
        best_idx = int(np.argmax(probs))
        confidence = float(probs[best_idx])
        category = _keyword_model.classes_[best_idx]

        if confidence >= 0.4:
            return str(category), round(confidence, 4)
    except Exception:
        pass

    kw_scores = {cat: 0 for cat in CATEGORIES}
    text_lower = text.lower()
    for cat, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if kw.lower() in text_lower:
                kw_scores[cat] += 1

    best_cat = max(kw_scores, key=kw_scores.get)
    best_score = kw_scores[best_cat]
    total = sum(kw_scores.values())

    if best_score > 0 and total > 0:
        return best_cat, round(best_score / total * 0.7, 4)

    return "altro", 0.5


def estimate_priority(text: str) -> Tuple[str, float]:
    text_lower = text.lower()
    scores = {p: 0 for p in PRIORITIES}

    for priority, keywords in PRIORITY_KEYWORDS.items():
        for kw in keywords:
            if kw.lower() in text_lower:
                scores[priority] += 1

    max_score = max(scores.values())
    if max_score == 0:
        return "p3", 0.6

    best_priority = max(scores, key=scores.get)
    total_priority = sum(scores.values())
    confidence = round(max_score / total_priority, 4) if total_priority > 0 else 0.5

    return best_priority, min(confidence, 1.0)


def retrain(training_data: List[Tuple[str, str]]):
    global _keyword_model
    if training_data:
        combined = SEED_TRAINING_DATA + training_data
    else:
        combined = SEED_TRAINING_DATA

    texts = [t for t, _ in combined]
    labels = [l for _, l in combined]

    pipeline = Pipeline([
        ("tfidf", TfidfVectorizer(
            max_features=5000,
            ngram_range=(1, 2),
            preprocessor=_preprocess,
            stop_words=ITALIAN_STOP_WORDS,
        )),
        ("clf", LogisticRegression(max_iter=1000, multi_class="multinomial")),
    ])
    pipeline.fit(texts, labels)
    joblib.dump(pipeline, MODEL_PATH)
    _keyword_model = pipeline


def analyze(title: str, description: str):
    combined = f"{title} {description}"

    keywords = extract_keywords(combined)
    category, cat_confidence = classify_category(combined)
    priority, pri_confidence = estimate_priority(combined)

    confidence = round((cat_confidence + pri_confidence) / 2, 4)

    return {
        "keywords": keywords,
        "category_slug": category,
        "priority": priority,
        "confidence": min(confidence, 1.0),
    }
