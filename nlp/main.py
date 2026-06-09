from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional

from analyzer import analyze, CATEGORIES, PRIORITIES

app = FastAPI(title="McTicket NLP Service")


class Ticket(BaseModel):
    title: str
    description: str
    category_hint: Optional[str] = None
    urgency_hint: Optional[str] = None


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
def analyze_batch(tickets: List[Ticket]):
    return [analyze(t.title, t.description) for t in tickets]


@app.get("/categories")
def get_categories():
    return {"categories": CATEGORIES}


@app.get("/priorities")
def get_priorities():
    return {"priorities": PRIORITIES}
