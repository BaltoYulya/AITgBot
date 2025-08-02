function leadManager(context) {
    this.$ = context;

    // Инициализация нового агента, если его ещё нет
    if (!this.$.session.lead) {
        this.lead = {}
        this.$.session.lead = this.lead;
    } else {
        // Если агент уже есть — используем его
        this.lead = this.$.session.lead;
    }
}


leadManager.prototype.createLead = function (param) {
    var param = {
        "user_channel": param.channel, // telegram TELEGRAM1, whatsapp WHATSAPP1
        "nameOpenAI": param.nameOpenAI,
        "id_telegram": param.id_telegram,
        "userName": param.userName,
        "userPhone": param.userPhone,
        "userThemes": param.userThemes,
        "userMicroThemes": param.userMicroThemes,
        "userUrgencyLevels": param.userUrgencyLevels
    };
    var newLead = {
        fields: {
            TITLE: "JAICP",
            NAME: param.userName,
            PHONE: [
                {
                    VALUE: param.userPhone,
                    VALUE_TYPE: "MOBILE"
                }
            ],
            SOURCE_ID: param.user_channel, // WHATSAPP1, TELEGRAM1
            UF_CRM_1753724408: param.id_telegram, // ID Telegram
            UF_CRM_1753768818: param.userName, // TG USERNAME
            UF_CRM_1750364723: param.userThemes, // Тематика
            UF_CRM_1750364784: param.userMicroThemes, // Микротематика
            UF_CRM_1753211933: param.userUrgencyLevels // Уровень срочности
        }

    }

    this.lead = newLead;
    this.$.session.lead = newLead;

    log('[+++] 🚀🚀🚀 Create Lead = ' + toPrettyString(newLead));

    // Отправляем лид в Битрикс
    return this.sendToBitrix(newLead);
};

/**
 * Отправляет лид в Битрикс24
 * @param {Object} leadData - данные лида
 * @returns {Object} ответ от API
 */
leadManager.prototype.sendToBitrix = function (leadData) {
    try {
        var param = getRequestParam();
        var url = replaceTokenInUrl(param.url.createLead, param.token.bitrix);

        log('[+++] 🚀🚀🚀 Create Lead URL = ' + url);
        log('[+++] 🚀🚀🚀 Create Lead Body = ' + toPrettyString(leadData));

        var response = $http.post(url, {
            headers: {
                'Content-Type': 'application/json'
            },
            body: leadData
        });

        log('[+++] 🚀🚀🚀 Create Lead Response = ' + toPrettyString(response));

        return response;
    } catch (error) {
        log('[+++] Error in sendToBitrix = ' + toPrettyString(error));
        return {
            error: error.message,
            status: error.status || 500
        };
    }
};

/**
 * Обновляет данные лида в Битрикс24
 * @param {string|number} leadId - ID лида для обновления
 * @param {Object} updateData - данные для обновления
 * @returns {Object} ответ от API
 */
leadManager.prototype.updateLead = function (leadId, updateData) {
    try {
        log('[+++] 🚀 Начинаем updateLead для ID: ' + leadId);
        
        var param = getRequestParam();
        var url = replaceTokenInUrl(param.url.updateLead, param.token.bitrix);
        log('[+++] 🚀 Сформировали URL: ' + url);

        log('[+++] 🚀 Формируем requestBody...');
        var requestBody = {
            id: leadId,
            fields: updateData
        };
        log('[+++] 🚀 requestBody создан успешно');

        log('[+++] 🔄🔄🔄 Update Lead URL = ' + url);
        log('[+++] 🔄🔄🔄 Update Lead ID = ' + leadId);
        log('[+++] 🔄🔄🔄 Update Lead Body = ' + toPrettyString(requestBody));
        log('[+++] 🚀 ОТПРАВЛЯЕМ ЗАПРОС В БИТРИКС: ' + JSON.stringify(requestBody));

        // Проверяем URL и параметры
        if (!url || !leadId || !updateData) {
            log('[ERROR] Отсутствуют обязательные параметры:');
            log('[ERROR] url = ' + url);
            log('[ERROR] leadId = ' + leadId);
            log('[ERROR] updateData = ' + toPrettyString(updateData));
            throw new Error('Отсутствуют обязательные параметры для обновления лида');
        }

        var response = $http.post(url, {
            headers: {
                'Content-Type': 'application/json'
            },
            body: requestBody,
            timeout: 10000 // 10 секунд таймаут
        });

        log('[+++] 🔄🔄🔄 Update Lead Response = ' + toPrettyString(response));

        // Проверяем ответ
        if (!response || !response.isOk) {
            log('[ERROR] Ошибка HTTP запроса к Bitrix:');
            log('[ERROR] response = ' + toPrettyString(response));
            throw new Error('Ошибка HTTP запроса к Bitrix: ' + (response ? response.error : 'Нет ответа'));
        }

        // Проверяем результат
        if (!response.data || !response.data.result) {
            log('[ERROR] Некорректный ответ от Bitrix:');
            log('[ERROR] response.data = ' + toPrettyString(response.data));
            throw new Error('Некорректный ответ от Bitrix');
        }

        // Обновляем данные в сессии, если обновление успешно
        if (this.lead && this.lead.fields) {
            // Обновляем локальные данные
            for (var key in updateData) {
                this.lead.fields[key] = updateData[key];
            }
            this.$.session.lead = this.lead;
            log('[+++] ✅ Данные успешно обновлены в сессии');
        }

        log('[+++] ✅ Успешное обновление лида в Bitrix');
        return response;

    } catch (error) {
        log('[+++] ❌ Error in updateLead:');
        log('[+++] ❌ error.message = ' + error.message);
        log('[+++] ❌ error.name = ' + error.name);
        log('[+++] ❌ error.stack = ' + error.stack);
        log('[+++] ❌ Full error object = ' + toPrettyString(error));
        throw error; // Пробрасываем ошибку дальше для обработки
    }
};

/**
 * Обновляет конкретные поля лида (вспомогательный метод)
 * @param {string|number} leadId - ID лида
 * @param {Object} fields - поля для обновления
 * @returns {Object} ответ от API
 */
leadManager.prototype.updateLeadFields = function (bitrix_id, leadData) {
    try {
        log('[+++] 🔄🔄🔄 лид который получили в класс = ' + toPrettyString(leadData));

        // Проверяем входные данные
        if (!bitrix_id || !leadData) {
            log('[ERROR] Отсутствуют обязательные параметры для updateLeadFields:');
            log('[ERROR] bitrix_id = ' + bitrix_id);
            log('[ERROR] leadData = ' + toPrettyString(leadData));
            throw new Error('Отсутствуют обязательные параметры для updateLeadFields');
        }

        var updateData = {};

        // Обновляем только переданные поля
        log('[+++] 🔍 Проверяем поля:');
        log('[+++] 🔍 leadData.userName = ' + leadData.userName);
        log('[+++] 🔍 leadData.userPhone = ' + leadData.userPhone);
        log('[+++] 🔍 leadData.userThemes = ' + leadData.userThemes);
        log('[+++] 🔍 leadData.userMicroThemes = ' + leadData.userMicroThemes);
        log('[+++] 🔍 leadData.userUrgencyLevels = ' + leadData.userUrgencyLevels);
        log('[+++] 🔍 leadData.STATUS_ID = ' + leadData.STATUS_ID);
        
        if (leadData.userName) {
            updateData.NAME = leadData.userName;
            log('[+++] ✅ Добавлено NAME: ' + leadData.userName);
        }
        if (leadData.userPhone) {
            updateData.PHONE = [{
                VALUE: leadData.userPhone,
                VALUE_TYPE: "MOBILE"
            }];
            log('[+++] ✅ Добавлено PHONE: ' + leadData.userPhone);
        }
        if (leadData.userThemes) {
            updateData.UF_CRM_1750364723 = leadData.userThemes;
            log('[+++] ✅ Добавлено UF_CRM_1750364723 (themes): ' + leadData.userThemes);
        }
        if (leadData.userMicroThemes) {
            updateData.UF_CRM_1750364784 = leadData.userMicroThemes;
            log('[+++] ✅ Добавлено UF_CRM_1750364784 (micro_themes): ' + leadData.userMicroThemes);
        }
        if (leadData.userUrgencyLevels) {
            updateData.UF_CRM_1753211933 = leadData.userUrgencyLevels;
            log('[+++] ✅ Добавлено UF_CRM_1753211933 (urgency): ' + leadData.userUrgencyLevels);
        }
        
        if (leadData.STATUS_ID) {
            updateData.STATUS_ID = leadData.STATUS_ID;
            log('[+++] ✅ Добавлено STATUS_ID: ' + leadData.STATUS_ID);
        }

        // Проверяем прямые поля Bitrix (например, UF_CRM_*)
        for (var key in leadData) {
            if (key.startsWith('UF_CRM_')) {
                updateData[key] = leadData[key];
                log('[+++] ✅ Добавлено прямое поле ' + key + ': ' + leadData[key]);
            }
        }

        log('[+++] 🔄🔄🔄 Updating fields = ' + toPrettyString(updateData));

        // Проверяем, есть ли что обновлять
        if (Object.keys(updateData).length === 0) {
            log('[WARNING] Нет данных для обновления в Bitrix');
            return null;
        }

        return this.updateLead(bitrix_id, updateData);
    } catch (error) {
        log('[ERROR] Ошибка в updateLeadFields:');
        log('[ERROR] error.message = ' + error.message);
        log('[ERROR] error.stack = ' + error.stack);
        throw error; // Пробрасываем ошибку дальше для обработки
    }
};