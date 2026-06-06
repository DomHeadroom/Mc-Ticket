from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional

from analyzer import analyze, retrain, CATEGORIES, PRIORITIES

app = FastAPI(title="McTicket NLP Service")


class Ticket(BaseModel):
    ticketId: int
    title: str
    description: str
    category_hint: Optional[str] = None
    urgency_hint: Optional[str] = None


class BatchTicket(BaseModel):
    title: str
    description: str
    category_hint: Optional[str] = None
    urgency_hint: Optional[str] = None


class TrainingExample(BaseModel):
    text: str
    category: str


class TrainRequest(BaseModel):
    examples: List[TrainingExample]


class AnalyzeResponse(BaseModel):
    keywords: List[str]
    category_slug: str
    priority: str
    confidence: float


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze_ticket(ticket: Ticket):
    return analyze(ticket.title, ticket.description)


@app.post("/analyze-batch", response_model=List[AnalyzeResponse])
def analyze_batch(tickets: List[BatchTicket]):
    return [analyze(t.title, t.description) for t in tickets]


@app.post("/train")
def train_model(req: TrainRequest):
    for ex in req.examples:
        if ex.category not in CATEGORIES:
            return {
                "error": f"Invalid category '{ex.category}'. Must be one of {CATEGORIES}"
            }
    data = [(ex.text, ex.category) for ex in req.examples]
    retrain(data)
    return {"status": "ok", "trained_examples": len(data)}


@app.get("/categories")
def get_categories():
    return {"categories": CATEGORIES}


@app.get("/priorities")
def get_priorities():
    return {"priorities": PRIORITIES}
