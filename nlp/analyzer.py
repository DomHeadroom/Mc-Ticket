import re
import os
from pathlib import Path
from typing import List, Tuple

import yake
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.calibration import CalibratedClassifierCV
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
        "cablaggio", "switch", "wi-fi", "wifi", "lan", "wan", "ping", "timeout",
        "latenza", "banda", "tcp", "ip", "dhcp", "nat", "gateway",
        "access point", "connettività", "vlan", "arp", "bgp", "ospf",
        "interfaccia di rete", "scheda di rete", "hotspot", "ssid",
        "pacchetto perso", "packet loss", "jitter", "throughput",
    ],
    "database": [
        "database", "db", "sql", "query", "mysql", "postgresql",
        "oracle", "mongodb", "tabella", "indice", "trigger",
        "procedura", "vista", "transazione", "lock", "deadlock",
        "connessione db", "errore database", "dati", "migrazione dati",
        "backup", "restore", "select", "insert", "update", "delete",
        "schema", "catalogo", "stored procedure", "replica", "sharding",
        "mariadb", "sqlite", "redis", "cassandra", "elasticsearch",
        "join", "foreign key", "primary key", "constraint", "rollback",
        "commit", "dump", "import", "export", "lentezza query",
    ],
    "bug-applicativo": [
        "bug", "errore", "eccezione", "crash", "stacktrace",
        "null pointer", "segmentation fault", "non funziona",
        "malfunzionamento", "anomalia", "errore software",
        "fallimento", "pianterello", "si blocca",
        "non risponde", "freeze", "loop infinito", "memory leak",
        "out of memory", "errore 500", "errore 404", "pagina bianca",
        "schermata nera", "applicazione si chiude", "comportamento strano",
        "risultato errato", "calcolo sbagliato", "dato errato",
        "regressione", "difetto", "problema software",
    ],
    "configurazione": [
        "configurazione", "config", "impostazione", "settaggio",
        "parametro", "proprietà", "variabile d'ambiente",
        "setup", "installazione", "deploy", "aggiornamento",
        "upgrade", "migrazione", "ambiente", "server",
        "avvio", "start", "restart", "config file",
        "application.properties", "yml", "yaml", "json config",
        "dockerfile", "docker compose", "kubernetes", "helm",
        "variabile", "secret", "credenziale", "licenza",
        "registro di sistema", "registry", "systemd", "daemon",
    ],
    "hardware": [
        "hardware", "stampante", "monitor", "schermo", "tastiera",
        "mouse", "disco", "ssd", "hdd", "ram", "cpu", "scheda",
        "alimentatore", "periferica", "rotto", "guasto",
        "surriscaldamento", "ventola", "componente", "fisico",
        "server spento", "hard disk", "usb", "porta usb",
        "batteria", "ups", "rack", "blade", "server fisico",
        "cavo", "connettore", "switch fisico", "patch panel",
        "token", "smart card", "lettore badge", "webcam",
    ],
    "servizi-web": [
        "web", "api", "endpoint", "http", "https", "rest", "soap",
        "servizio web", "certificato", "ssl", "tls", "dominio",
        "url", "cloud", "saas", "portale", "sito", "502", "503",
        "servizio online", "gateway", "graphql", "microservizio",
        "webhook", "oauth", "token jwt", "cors", "cdn",
        "nginx", "apache", "iis", "load balancer", "reverse proxy",
        "dns record", "a record", "cname", "hosting", "ftp",
        "404", "401", "403", "500", "risposta lenta",
    ],
}

PRIORITY_KEYWORDS = {
    "p1": [
        "critico", "bloccante", "produzione ferma", "down",
        "non accessibile", "immediato", "fermo", "bloccato",
        "grave", "disastro", "emergenza", "urgentissimo",
        "sistema giù", "fuori servizio", "totalmente inaccessibile",
        "perdita dati", "data loss", "sicurezza compromessa",
        "breach", "attacco", "hacked",
    ],
    "p2": [
        "alto", "importante", "urgente", "serio", "significativo",
        "priorità", "considerevole", "entro oggi", "entro domani",
        "impatta molti utenti", "degradato", "parzialmente funzionante",
    ],
    "p3": [
        "medio", "normale", "moderato", "standard", "regolare",
        "quando possibile", "non urgente", "comodo",
    ],
    "p4": [
        "basso", "minore", "cosmetico", "trascurabile",
        "miglioramento", "richiesta", "in futuro", "nice to have",
        "suggerimento", "proposta",
    ],
}

SEED_TRAINING_DATA: List[Tuple[str, str]] = [
    # ---- RETE ----
    ("La connessione VPN non funziona più da questa mattina", "rete"),
    ("Il router in ufficio si riavvia continuamente", "rete"),
    ("Problemi di latenza sulla rete LAN", "rete"),
    ("Non riesco a connettermi al server tramite SSH", "rete"),
    ("La rete Wi-Fi non copre tutto l'ufficio", "rete"),
    ("Ping verso il gateway principale supera i 500ms", "rete"),
    ("Il firewall blocca le connessioni in uscita sulla porta 443", "rete"),
    ("Rete instabile dopo l'aggiornamento del firmware dello switch", "rete"),
    ("La VPN aziendale cade ogni 10 minuti", "rete"),
    ("Impossibile raggiungere la rete interna dall'esterno", "rete"),
    ("Il DNS non risolve i nomi interni", "rete"),
    ("Lentezza anomala sulla connessione internet", "rete"),
    ("Lo switch di piano non instrada i pacchetti correttamente", "rete"),
    ("Packet loss elevato tra i due datacenter", "rete"),
    ("La VLAN di produzione è irraggiungibile", "rete"),
    ("Il DHCP non assegna indirizzi IP ai nuovi dispositivi", "rete"),
    ("Connessione WiFi interrotta frequentemente in sala riunioni", "rete"),
    ("Throughput della rete WAN dimezzato rispetto a ieri", "rete"),
    ("Il proxy aziendale non autentica correttamente gli utenti", "rete"),
    ("Jitter elevato sulle chiamate VoIP causa problemi audio", "rete"),
    ("Il NAT sul firewall non funziona per il traffico verso l'esterno", "rete"),
    ("Access point in sala server offline da stamattina", "rete"),
    ("La connessione al sito remoto tramite VPN site-to-site è caduta", "rete"),
    ("Elevata latenza sul collegamento MPLS verso la sede di Milano", "rete"),
    ("Impossibile risolvere gli host interni tramite DNS aziendale", "rete"),

    # ---- DATABASE ----
    ("La query SQL va in timeout dopo 30 secondi", "database"),
    ("Il database PostgreSQL non risponde più", "database"),
    ("Errore di connessione al database in produzione", "database"),
    ("Le tabelle del database si sono corrotte", "database"),
    ("Problemi di locking sulle transazioni", "database"),
    ("La replica del database è in ritardo di 10 minuti", "database"),
    ("Errore ORA-00001: unique constraint violato su tabella utenti", "database"),
    ("La query di ricerca impiega più di un minuto per restituire risultati", "database"),
    ("Il backup del database non è andato a buon fine stanotte", "database"),
    ("Deadlock rilevato sulle transazioni della tabella ordini", "database"),
    ("Lo spazio su disco del database è quasi esaurito", "database"),
    ("La stored procedure di calcolo restituisce valori errati", "database"),
    ("Migrazione dati fallita durante l'upgrade di versione", "database"),
    ("Il database MySQL è andato in crash e non si riavvia", "database"),
    ("Errore di connessione: troppi client connessi al database", "database"),
    ("La replica master-slave è desincronizzata", "database"),
    ("Query con join su tabelle grandi impiega 5 minuti", "database"),
    ("La foreign key violation impedisce l'inserimento dei dati", "database"),
    ("Il rollback della transazione non funziona correttamente", "database"),
    ("Il catalogo del database risulta danneggiato dopo il crash", "database"),
    ("Indice mancante causa scansione completa della tabella", "database"),
    ("Il dump del database è corrotto e non importabile", "database"),
    ("Errore di autenticazione al database: credenziali non valide", "database"),
    ("La vista materializzata non si aggiorna automaticamente", "database"),
    ("Il trigger di audit non scrive correttamente nel log", "database"),

    # ---- BUG-APPLICATIVO ----
    ("L'applicazione crasha quando carico un file", "bug-applicativo"),
    ("Errore NullPointerException nella homepage", "bug-applicativo"),
    ("Il pulsante di salvataggio non funziona", "bug-applicativo"),
    ("Stacktrace quando apro la schermata di report", "bug-applicativo"),
    ("L'applicazione non risponde più dopo l'aggiornamento", "bug-applicativo"),
    ("Il form di login non valida correttamente le credenziali", "bug-applicativo"),
    ("Errore 500 all'apertura della pagina dei dettagli ordine", "bug-applicativo"),
    ("I dati mostrati nella dashboard non sono aggiornati", "bug-applicativo"),
    ("L'app va in loop infinito durante il caricamento della pagina", "bug-applicativo"),
    ("Memory leak rilevato nel modulo di reportistica", "bug-applicativo"),
    ("Il calcolo del totale nella fattura è sbagliato", "bug-applicativo"),
    ("La funzione di esportazione Excel produce un file corrotto", "bug-applicativo"),
    ("Il modulo di invio email non invia i messaggi", "bug-applicativo"),
    ("La ricerca avanzata restituisce risultati errati", "bug-applicativo"),
    ("L'applicazione si chiude improvvisamente alla pressione di un tasto", "bug-applicativo"),
    ("Il caricamento dell'immagine del profilo causa un errore 500", "bug-applicativo"),
    ("Il filtro per data nel report non funziona correttamente", "bug-applicativo"),
    ("L'interfaccia utente si blocca dopo aver aperto 10 tab", "bug-applicativo"),
    ("La notifica push non viene recapitata all'utente", "bug-applicativo"),
    ("Il modulo di pagamento mostra un errore sconosciuto", "bug-applicativo"),
    ("La funzione di ricerca non restituisce risultati anche con dati presenti", "bug-applicativo"),
    ("Il contatore delle notifiche non si azzera dopo la lettura", "bug-applicativo"),
    ("L'applicazione mobile va in crash all'avvio su Android 14", "bug-applicativo"),
    ("Il log mostra una ClassNotFoundException all'avvio dell'applicazione", "bug-applicativo"),
    ("La paginazione della lista utenti salta alcune righe", "bug-applicativo"),

    # ---- CONFIGURAZIONE ----
    ("Devo aggiornare i parametri di configurazione del server", "configurazione"),
    ("Impostare le variabili d'ambiente per il nuovo deploy", "configurazione"),
    ("Configurare il proxy per l'accesso a internet", "configurazione"),
    ("Modificare le proprietà dell'applicazione in produzione", "configurazione"),
    ("Installazione e setup del nuovo ambiente di sviluppo", "configurazione"),
    ("Configurazione del certificato SSL per il dominio", "configurazione"),
    ("Parametrizzare l'URL del database nel file di properties", "configurazione"),
    ("Aggiungere il nuovo endpoint alla configurazione del load balancer", "configurazione"),
    ("La variabile d'ambiente DATABASE_URL non è impostata correttamente", "configurazione"),
    ("Il file di configurazione YAML ha una sintassi errata", "configurazione"),
    ("Deploy fallito perché il Dockerfile non trova la dipendenza", "configurazione"),
    ("Il servizio systemd non si avvia: configurazione errata nel unit file", "configurazione"),
    ("Aggiornare i secret di Kubernetes per il nuovo ambiente", "configurazione"),
    ("La licenza del software è scaduta e blocca l'avvio", "configurazione"),
    ("Configurare il cron job per l'esecuzione notturna del backup", "configurazione"),
    ("Il registro di sistema Windows ha una chiave errata che impedisce l'avvio", "configurazione"),
    ("Aggiornare il file hosts per il nuovo indirizzo del server applicativo", "configurazione"),
    ("Configurazione SMTP mancante: le email non vengono inviate", "configurazione"),
    ("Il timeout della sessione è troppo basso: gli utenti vengono scollegati spesso", "configurazione"),
    ("Il file application.properties punta ancora all'ambiente di test", "configurazione"),
    ("Impostare i permessi corretti sulla cartella di upload", "configurazione"),
    ("Configurare nginx come reverse proxy per il nuovo servizio", "configurazione"),
    ("Il Helm chart non include la variabile di configurazione del shard", "configurazione"),
    ("Aggiornare la configurazione del pool di connessioni al database", "configurazione"),
    ("Il servizio non parte perché manca il file di configurazione obbligatorio", "configurazione"),

    # ---- HARDWARE ----
    ("La stampante non stampa più", "hardware"),
    ("Il monitor rimane nero all'accensione", "hardware"),
    ("Il disco SSD non viene rilevato dal sistema", "hardware"),
    ("La ventola del server fa molto rumore", "hardware"),
    ("La tastiera non funziona dopo l'ultimo aggiornamento", "hardware"),
    ("Il computer si surriscalda e si spegne", "hardware"),
    ("Il mouse si disconnette continuamente", "hardware"),
    ("L'alimentatore del PC ha smesso di funzionare", "hardware"),
    ("Il server fisico non si accende nonostante i tentativi", "hardware"),
    ("La scheda di rete è bruciata e va sostituita", "hardware"),
    ("Il disco rigido ha settori danneggiati", "hardware"),
    ("La RAM del server mostra errori nei test di diagnostica", "hardware"),
    ("Il cavo di alimentazione del rack è difettoso", "hardware"),
    ("La batteria del laptop si scarica in meno di un'ora", "hardware"),
    ("L'UPS non si carica e non fornisce backup di alimentazione", "hardware"),
    ("La webcam non viene rilevata dal sistema operativo", "hardware"),
    ("Il lettore di smart card non legge il badge aziendale", "hardware"),
    ("Il token USB per la firma digitale non funziona", "hardware"),
    ("Il monitor ha delle strisce verticali sullo schermo", "hardware"),
    ("La stampante di rete mostra errore di carta inceppata", "hardware"),
    ("Il processore raggiunge il 100% di utilizzo e va in throttling", "hardware"),
    ("Il connettore della porta USB del laptop è rotto", "hardware"),
    ("Il server blade non viene riconosciuto dallo chassis", "hardware"),
    ("La scheda grafica produce artefatti visivi sullo schermo", "hardware"),
    ("Il disco NAS è in stato degradato dopo il guasto di un drive", "hardware"),

    # ---- SERVIZI-WEB ----
    ("Il sito web non risponde sulla porta 443", "servizi-web"),
    ("Il certificato SSL è scaduto e blocca l'accesso", "servizi-web"),
    ("L'API REST non restituisce risultati", "servizi-web"),
    ("L'endpoint di autenticazione va in timeout", "servizi-web"),
    ("Il servizio web è irraggiungibile dall'esterno", "servizi-web"),
    ("Il portale aziendale mostra errore 502", "servizi-web"),
    ("Le chiamate API sono lentissime", "servizi-web"),
    ("Il certificato TLS non è valido per il dominio", "servizi-web"),
    ("Il webhook non riceve le notifiche dal provider esterno", "servizi-web"),
    ("L'autenticazione OAuth2 fallisce con errore invalid_grant", "servizi-web"),
    ("Il CDN non serve i file statici aggiornati", "servizi-web"),
    ("Il record DNS CNAME non è propagato correttamente", "servizi-web"),
    ("Il load balancer distribuisce le richieste in modo non uniforme", "servizi-web"),
    ("Il servizio SOAP risponde con errore 500 a tutte le richieste", "servizi-web"),
    ("Il token JWT non viene validato correttamente dal backend", "servizi-web"),
    ("L'endpoint GraphQL restituisce errori di schema", "servizi-web"),
    ("Il sito mostra errore CORS su tutte le chiamate Ajax", "servizi-web"),
    ("Il servizio FTP non accetta connessioni passive", "servizi-web"),
    ("Il reverse proxy nginx restituisce 504 Gateway Timeout", "servizi-web"),
    ("Il microservizio di pagamento non risponde alle chiamate interne", "servizi-web"),
    ("L'API di terze parti restituisce 403 Forbidden inaspettato", "servizi-web"),
    ("Il portale di e-commerce mostra errore 503 durante il picco di traffico", "servizi-web"),
    ("Il servizio di autenticazione SSO è irraggiungibile", "servizi-web"),
    ("Le chiamate REST al backend falliscono con errore 401 dopo il login", "servizi-web"),
    ("Il gateway API non applica correttamente il rate limiting", "servizi-web"),

    # ---- ALTRO ----
    ("Richiesta di informazioni sul progetto", "altro"),
    ("Documentazione da aggiornare", "altro"),
    ("Nuovo utente da registrare al sistema", "altro"),
    ("Domanda sulle policy aziendali di sicurezza", "altro"),
    ("Richiesta di accesso al repository Git", "altro"),
    ("Formazione necessaria per il nuovo software", "altro"),
    ("Richiesta di ferie e permessi", "altro"),
    ("Richiesta di un nuovo monitor per la postazione di lavoro", "altro"),
    ("Aggiornamento del manuale utente del gestionale", "altro"),
    ("Creazione di un nuovo account per il collaboratore esterno", "altro"),
    ("Richiesta di acquisto di una nuova licenza software", "altro"),
    ("Domanda su come esportare i dati dal vecchio sistema", "altro"),
    ("Organizzare una sessione di formazione sul nuovo CRM", "altro"),
    ("Richiedere un aumento dello spazio su disco sulla condivisione di rete", "altro"),
    ("Aggiornare il diagramma dell'architettura di sistema", "altro"),
    ("Richiesta di un certificato di lavoro per l'ufficio HR", "altro"),
    ("Domanda su quando uscirà il prossimo aggiornamento del software", "altro"),
    ("Segnalazione di un comportamento insolito ma non bloccante", "altro"),
    ("Richiesta di abilitare l'autenticazione a due fattori per l'account", "altro"),
    ("Domanda sulle modalità di archiviazione dei documenti aziendali", "altro"),
]


def _preprocess(text: str) -> str:
    text = text.lower()
    text = re.sub(r"[^a-zàèéìíîòóùúçë0-9\s\-]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _train_model():
    texts = [t for t, _ in SEED_TRAINING_DATA]
    labels = [l for _, l in SEED_TRAINING_DATA]

    base_clf = LogisticRegression(
        max_iter=2000,
        multi_class="multinomial",
        class_weight="balanced",
        solver="lbfgs",
        C=5.0,
    )

    calibrated_clf = CalibratedClassifierCV(base_clf, cv=5, method="isotonic")

    pipeline = Pipeline([
        ("tfidf", TfidfVectorizer(
            max_features=8000,
            ngram_range=(1, 3),
            preprocessor=_preprocess,
            stop_words=ITALIAN_STOP_WORDS,
            sublinear_tf=True,
            min_df=1,
            analyzer="word",
        )),
        ("clf", calibrated_clf),
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

        if confidence >= 0.35:
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
