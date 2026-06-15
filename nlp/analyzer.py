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

from data import ITALIAN_STOP_WORDS, CATEGORIES, PRIORITIES, CATEGORY_KEYWORDS, PRIORITY_KEYWORDS, SEED_TRAINING_DATA

MODEL_DIR = Path(os.getenv("MODEL_DIR", "/app/models"))
MODEL_DIR.mkdir(parents=True, exist_ok=True)
MODEL_PATH = MODEL_DIR / "classifier.joblib"

kw_extractor = yake.KeywordExtractor(lan="it", n=3, dedupLim=0.9, top=10)


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


_category_classifier = _load_model()


def classify_category(text: str) -> Tuple[str, float]:
    preprocessed = _preprocess(text)

    try:
        probs = _category_classifier.predict_proba([preprocessed])[0]
        best_idx = int(np.argmax(probs))
        confidence = float(probs[best_idx])
        category = _category_classifier.classes_[best_idx]

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
