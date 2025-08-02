function getRequestParam() {
    var $ = $jsapi.context()
    return {
        url: $.injector.api,
        token: $.injector.auth
    }
}

/**
 * Заменяет плейсхолдер {token} в URL шаблоне на переданное значение токена
 * @param {string} urlTemplate - URL шаблон с плейсхолдером {token}
 * @param {string} tokenValue - значение токена для подстановки
 * @returns {string} URL с подставленным токеном
 */
function replaceTokenInUrl(urlTemplate, tokenValue) {
    return urlTemplate.replace('{token}', tokenValue);
}


// Проверяет, что arr - массив, причем не пустой
function isNotEmptyArray(arr) {
    return _.isArray(arr) && !_.isEmpty(arr);
}


function extractPromts(data) {
    var result = {};

    _.each(data, function (value, key) {
        result[key] = value.promt;
    });

    return JSON.stringify(result, null, 2); // красиво отформатированная строка JSON
}

function askBrain(system_prompt, messages) {
    try {
        // "gpt-4.1-nano",
        var url = "http://109.237.98.241:5010/ask";

        var body = {
            system_prompt: system_prompt,
            model: "gpt-4.1",
            messages: messages
        };

        var response = $http.post(url, {
            headers: {
                'Content-Type': 'application/json'
            },
            body: body,
            timeout: 25000  // 25 секунд таймаут (максимум по документации)
        });
        log('[+++] 🧠🧠🧠 REQUEST BODY = ' + toPrettyString(body));
        log('[+++] 🧠🧠🧠 FULL RESPONSE = ' + toPrettyString(response));
        log('[+++] 🧠🧠🧠 response.data = ' + toPrettyString(response.data));

        // Проверяем статус HTTP запроса
        if (!response.isOk || response.error) {
            log('[ERROR] Ошибка HTTP запроса: ' + toPrettyString(response.error || 'Неизвестная ошибка'));
            return {
                error: response.error || "HTTP запрос не выполнен",
                status: response.status || 500
            };
        }

        // Новый упрощенный формат: сервер возвращает полный JSON объект напрямую
        if (response.data) {
            log('[+++] 🧠🧠🧠 Получен ответ от сервера: ' + toPrettyString(response.data));
            
            // Если это объект с полями userResponse, data, status - возвращаем его
            if (typeof response.data === 'object' && 
                (response.data.userResponse || response.data.data || response.data.status)) {
                log('[+++] 🧠🧠🧠 Найден полный JSON объект с нужными полями');
                return response.data;
            }
            
            // Если это объект с ошибкой - возвращаем его
            if (typeof response.data === 'object' && response.data.error) {
                log('[+++] 🧠🧠🧠 Получена ошибка от сервера');
                return response.data;
            }
            
            // Иначе возвращаем как есть
            log('[+++] 🧠🧠🧠 Возвращаем response.data как есть');
            return response.data;
        }
        
        // Fallback - если data пустая
        log('[+++] 🧠🧠🧠 response.data пустая, возвращаем ошибку');
        return {
            error: "Пустой ответ от сервера",
            status: 500
        };



    } catch (error) {
        return {
            "error": error.message,
            "status": error.status || 500
        };
    }
}

/**
 * Получает список пользовательских полей из Битрикс24
 * @returns {Object} ответ от API с полями
 */
function getBitrixUserFields() {

        var param = getRequestParam()
        var url = replaceTokenInUrl(param.url.getBitrixThemes, param.token.bitrix);

        var response = $http.post(url, {
            headers: {
                'Cookie': 'qmb=0.'
            }
        });
        
        

        if (response && response.data && response.data.result) {
            return response.data.result;
        }

        return null;

}

/**
 * Извлекает тематики, микро-тематики и срочность из ответа Битрикс24
 * @param {Array} result - массив полей из ответа Битрикс24
 * @returns {Object} объект с тематиками, микро-тематиками и срочностью
 */
function extractThemesFromBitrix(result) {
    var themes = [];
    var microThemes = [];
    var urgencyLevels = [];

    try {
        _.each(result, function (field) {
            // Проверяем, что это поле с перечислением (enumeration)
            if (field.USER_TYPE_ID === "enumeration" && field.LIST && Array.isArray(field.LIST)) {

                // Определяем тип поля по FIELD_NAME
                if (field.FIELD_NAME === "UF_CRM_1750364723") {
                    // Основные тематики
                    _.each(field.LIST, function (item) {
                        themes.push({
                            "id": item.ID,
                            "value": item.VALUE
                        });
                    });
                } else if (field.FIELD_NAME === "UF_CRM_1750364784") {
                    // Микро-тематики
                    _.each(field.LIST, function (item) {
                        microThemes.push({
                            "id": item.ID,
                            "value": item.VALUE
                        });
                    });
                } else if (field.FIELD_NAME === "UF_CRM_1753211933") {
                    // Уровни срочности
                    _.each(field.LIST, function (item) {
                        urgencyLevels.push({
                            "id": item.ID,
                            "value": item.VALUE
                        });
                    });
                }
            }
        });

        log('[+++] Extracted Themes = ' + toPrettyString(themes));
        log('[+++] Extracted Micro Themes = ' + toPrettyString(microThemes));
        log('[+++] Extracted Urgency Levels = ' + toPrettyString(urgencyLevels));

        return {
            "themes": themes,
            "microThemes": microThemes,
            "urgencyLevels": urgencyLevels
        };

    } catch (error) {
        log('[+++] Error extracting themes = ' + toPrettyString(error));
        return {
            "themes": [],
            "microThemes": [],
            "urgencyLevels": [],
            "error": error.message
        };
    }
}




function getUserByBitrix(id, type) {
    var param = getRequestParam();

    var headers = {
        'Cookie': 'qmb=0.',
        'Content-Type': 'application/x-www-form-urlencoded'
    };

    if (type === 'whatsapp') {
        // Для WhatsApp используем crm.duplicate.findbycomm
        var url = replaceTokenInUrl(param.url.getUserByWhatsapp, param.token.bitrix);
        
        var response = $http.post(url, {
            headers: headers,
            form: {
                'entity_type': 'LEAD',
                'type': 'PHONE',
                'values[0]': id
            }
        });

    } else if (type === 'telegram') {
        // Для Telegram используем crm.lead.list
        var url = replaceTokenInUrl(param.url.getUserByTelegram, param.token.bitrix);
        
        // Создаем form данные как строку, так как у нас есть дублирующиеся ключи
        var formData = 'filter[UF_CRM_TELEGRAMID_WZ]=' + encodeURIComponent(id) + 
                      '&select[]=ID' +
                      '&select[]=TITLE' +
                      '&select[]=UF_CRM_TELEGRAMID_WZ' +
                      '&order[ID]=DESC' +
                      '&start=0';
        
        var response = $http.post(url, {
            headers: headers,
            body: formData
        });
       
    }

    // унифицируем ответ

    if (type === 'whatsapp' && response.data && response.data.result.LEAD && response.data.result.LEAD.length === 1) {
        return response.data.result.LEAD[0];
    } else if (type === 'telegram' && response.data && response.data.result.length === 1) {
        return response.data.result[0].ID;
    }
    return null;
}

/**
 * Извлекает JSON объект из текста и возвращает отдельно текст и JSON
 * @param {string} input - входная строка с текстом и JSON объектом
 * @returns {Object} объект с полями response (текст без JSON) и json (извлеченный JSON объект)
 */
function parseResponseWithJson(input) {
    try {
        // Ищем начало JSON объекта
        var jsonStart = input.indexOf('{');
        
        if (jsonStart === -1) {
            // JSON не найден, возвращаем весь текст как response
            return {
                response: input.trim(),
                json: null
            };
        }
        
        // Ищем конец JSON объекта, учитывая вложенные объекты
        var braceCount = 0;
        var jsonEnd = -1;
        
        for (var i = jsonStart; i < input.length; i++) {
            if (input[i] === '{') {
                braceCount++;
            } else if (input[i] === '}') {
                braceCount--;
                if (braceCount === 0) {
                    jsonEnd = i;
                    break;
                }
            }
        }
        
        if (jsonEnd === -1) {
            // Не найден закрывающий символ, возвращаем весь текст
            return {
                response: input.trim(),
                json: null
            };
        }
        
        // Извлекаем JSON строку
        var jsonString = input.substring(jsonStart, jsonEnd + 1);
        
        // Формируем текст без JSON
        var beforeJson = input.substring(0, jsonStart).trim();
        var afterJson = input.substring(jsonEnd + 1).trim();
        var responseText = (beforeJson + (beforeJson && afterJson ? '\n\n' : '') + afterJson).trim();
        
        // Парсим JSON
        var parsedJson;
        try {
            parsedJson = JSON.parse(jsonString);
        } catch (parseError) {
            log('[+++] Error parsing JSON = ' + toPrettyString(parseError));
            return {
                response: input.trim(),
                json: null,
                error: 'Invalid JSON format'
            };
        }
        
        return {
            response: responseText,
            json: parsedJson
        };
        
    } catch (error) {
        log('[+++] Error in parseResponseWithJson = ' + toPrettyString(error));
        return {
            response: input.trim(),
            json: null,
            error: error.message
        };
    }
}





function getSystemPrompt(channel) {
   
    // TODO: добавить проверку, иначае openAI не будет получать полный контекст в случае ошибок
    var themes = getBitrixUserFields(); // Получаем тематику и микротематику, добмалвяем её к промту
    var themes_text = extractThemesFromBitrix(themes);
    var channelPrompt = channel === 'telegram' ? prompt.prompt_tg : prompt.prompt_whatsapp;
    return channelPrompt + "\n\n📋 ДОСТУПНЫЕ ТЕМАТИКИ И МИКРОТЕМАТИКИ:\n" + themes_text + "\n\nСтатусы:\n" + prompt.status;

}