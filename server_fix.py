import os
import time
import json
import dotenv
import openai
import httpx
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional, Dict

dotenv.load_dotenv()
# Устанавливаем таймаут 30 секунд для OpenAI клиента
client = openai.OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    timeout=30.0
)
app = FastAPI()


class ChatMessage(BaseModel):
    role: str
    content: str


class PromptInput(BaseModel):
    messages: List[ChatMessage]
    system_prompt: Optional[str] = None
    model: str = "gpt-4.1-nano"


def measure_openai_rtt() -> float:
    """RTT до OpenAI API в миллисекундах"""
    try:
        with httpx.Client(http2=True, timeout=10.0) as http_client:
            start = time.time()
            http_client.head("https://api.openai.com/v1/models")
            return round((time.time() - start) * 1000, 2)
    except Exception:
        return -1


@app.post("/ask")
async def ask_user(input: PromptInput):
    """
    Обрабатывает запрос и возвращает полный JSON-объект напрямую
    """
    # Добавляем инструкцию о формате ответа в system prompt
    format_instruction = """
        ВАЖНО: Твой ответ ДОЛЖЕН быть в формате JSON со следующей структурой:
        {
        "userResponse": "текст ответа пользователю",
        "data": {
            "client_name": null или строка,
            "client_phone": null или строка,
            "city": null или строка,
            "region": null или строка,
            "problem_category": null или строка,
            "problem_description": null или строка,
            "consultation_status": null или "да"/"нет"/"позже",
            "themes": [],
            "micro_themes": [],
            "additional": {
            "amount": null,
            "key_dates": [],
            "documents": null,
            "urgency": null,
            "emotional_state": null,
            "organization": null,
            "desired_result": null
            }
        },
        "status": "IN_PROCESS"
        }
        """
    system_prompt = (input.system_prompt or "") + "\n\n" + format_instruction

    user_texts = [m.content for m in input.messages if m.role == "user"]
    print(f"\n🟡 Новый запрос от клиента: {user_texts}")
    rtt = measure_openai_rtt()
    print(f"⏳ RTT (ping) до OpenAI: {rtt} мс")

    t0 = time.time()

    # Формируем запрос к OpenAI с полной историей сообщений
    try:
        completion = client.chat.completions.create(
            model=input.model,
            messages=[
                {"role": "system", "content": system_prompt.strip()},
                *[{"role": m.role, "content": m.content} for m in input.messages],
                {"role": "system", "content": "Ответь СТРОГО в формате JSON, описанном выше. Не добавляй никакого текста до или после JSON."}
            ],
            stream=False,
        )
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

    full_reply: str = completion.choices[0].message.content.strip()
    print(f"📤 Полный ответ модели:\n{full_reply}")

    # Пытаемся найти и распарсить JSON в ответе
    try:
        # Сначала пробуем парсить весь ответ как JSON
        try:
            result_data = json.loads(full_reply)
            if isinstance(result_data, dict) and "userResponse" in result_data:
                print("✅ Успешно распарсили полный JSON ответ")
                return JSONResponse(content=result_data, headers={"X-RTT-MS": str(rtt), "X-Total-Time": str(round(time.time() - t0, 2))})
        except json.JSONDecodeError:
            pass

        # Если не получилось, ищем JSON в тексте
        start_idx = full_reply.find('{')
        end_idx = full_reply.rfind('}')
        
        if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
            json_str = full_reply[start_idx:end_idx + 1]
            try:
                result_data = json.loads(json_str)
                if isinstance(result_data, dict) and "userResponse" in result_data:
                    print("✅ Успешно извлекли JSON из текста")
                    return JSONResponse(content=result_data, headers={"X-RTT-MS": str(rtt), "X-Total-Time": str(round(time.time() - t0, 2))})
            except json.JSONDecodeError:
                pass

        # Если все попытки не удались, создаем базовый ответ
        default_response = {
            "userResponse": full_reply,
            "data": {
                "client_name": None,
                "client_phone": None,
                "city": None,
                "region": None,
                "problem_category": None,
                "problem_description": None,
                "consultation_status": None,
                "themes": [],
                "micro_themes": [],
                "additional": {
                    "amount": None,
                    "key_dates": [],
                    "documents": None,
                    "urgency": None,
                    "emotional_state": None,
                    "organization": None,
                    "desired_result": None
                }
            },
            "status": "IN_PROCESS"
        }
        print("⚠️ Не удалось распарсить JSON, возвращаем дефолтный ответ")
        return JSONResponse(content=default_response, headers={"X-RTT-MS": str(rtt), "X-Total-Time": str(round(time.time() - t0, 2))})

    except Exception as e:
        error_response = {
            "error": "Ошибка обработки ответа",
            "raw_response": full_reply,
            "details": str(e),
            "status": 500
        }
        print(f"❌ Ошибка: {str(e)}")
        return JSONResponse(status_code=500, content=error_response) 