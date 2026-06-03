from fastapi import FastAPI
from pydantic import BaseModel
import orjson

app = FastAPI()

class Ticket(BaseModel):
    ticketId: int
    title: str
    description: str

@app.post("/analyze")
def analyze(ticket: Ticket):
    return {
        "keywords": ["vpn", "rete"],
        "category_slug": "rete",
        "priority": "p2",
        "confidence": 0.87
    }