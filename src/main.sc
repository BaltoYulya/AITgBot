require: requirements.sc

init:
    $global.$ = {
        __noSuchProperty__: function(property) {
            return $jsapi.context()[property];
        }
    };

    bind("preMatch", function($context) {
        var $ = $context;
        $.session.AUTH = $.injector.auth;
        $.session.API = $.injector.api;
    });

theme: /

    state: StartFirst

        q!: $regex<test>
        q!: test
        q!: 1
        a: Здравствуйте!. Возник вопрос?
        script:
            try {
                log('[+++] 🧠🧠🧠 request = ' + toPrettyString($request));

                // Определяем канал пользователя
                var userChannel = $request.rawRequest.chatType; // telegram или whatsapp

                if (userChannel == "telegram") {
                    // Получаем пользователя из Битрикса
                    var id_telegram = $request.userFrom.id;
                    $session.id_telegram = id_telegram;
                    var username = ($request.rawRequest &&
                                    $request.rawRequest.contact &&
                                    $request.rawRequest.contact.username &&
                                    $request.rawRequest.contact.username.trim() !== "")
                                   ? $request.rawRequest.contact.username
                                   : "";

                    // Проверяем, есть ли пользователь в Битриксе
                    var result = gerUserByBitrix(id_telegram);

                    if (_.isEmpty(result)) {
                        var leadManagerInstance = new leadManager($context);
                        leadManagerInstance.createLead({
                            channel: "TELEGRAM1",
                            id_telegram: id_telegram,
                            userName: username
                        });
                    } else {
                        // TODO: логика обработки существующего лида
                    }

                } else if (userChannel == "whatsapp") {
                    // TODO: логика для WhatsApp
                } else {
                    // TODO: другое подключение
                }

                var themes = getBitrixUserFields(); // Получаем тематику и микротематику
                var themes_text = extractThemesFromBitrix(themes);
                $session.themes_text = themes_text;

                $session.messages = [
                    {
                        role: "assistant",
                        content: "Здравствуйте!. Возник вопрос?"
                    }
                ];
            } catch (e) {
                $reactions.answer(toPrettyString(e.stack));
            }

    state: NoMatch
        event!: noMatch
        script:
            var userChannel = $request.rawRequest.chatType; // telegram или whatsapp

            if (userChannel == "telegram") {
                var id_telegram = $request.userFrom.id;
                $session.id_telegram = id_telegram;
                var username = ($request.rawRequest &&
                                $request.rawRequest.contact &&
                                $request.rawRequest.contact.username &&
                                $request.rawRequest.contact.username.trim() !== "")
                               ? $request.rawRequest.contact.username
                               : "";
            }

            // История переписки
            if (!$session.messages) {
                $session.messages = [];
            }
            $session.messages.push({
                role: "user",
                content: $request.query
            });

            // Тематики для промта
            var themes_data = "";
            if ($session.themes_text) {
                themes_data = JSON.stringify($session.themes_text);
            }

            var full_prompt = prompt.prompt_tg
                + "\n\n📋 ДОСТУПНЫЕ ТЕМАТИКИ И МИКРОТЕМАТИКИ:\n"
                + themes_data
                + "\n\nСтатусы:\n"
                + prompt.status;

            var maxRetries = 5;
            var currentRetry = 0;
            var tempRes, userResponse, data, status;

            while (currentRetry < maxRetries) {
                try {
                    tempRes = askBrain(full_prompt, $session.messages);

                    if (!tempRes) {
                        currentRetry++;
                        log('[ERROR] askBrain вернул пустой результат, попытка ' + currentRetry + ' из ' + maxRetries);
                        if (currentRetry >= maxRetries) {
                            $reactions.answer('Извините, не удалось получить ответ от ИИ. Попробуйте позже.');
                            return;
                        }
                        log('[DEBUG] Ждем 5 секунд...');
                        var start = Date.now();
                        while (Date.now() - start < 5000) {}
                        continue;
                    }

                    if (tempRes.error) {
                        currentRetry++;
                        log('[ERROR] askBrain вернул ошибку, попытка ' + currentRetry + ' из ' + maxRetries + ': ' + toPrettyString(tempRes));
                        if (currentRetry >= maxRetries) {
                            $reactions.answer('Извините, техническая ошибка. Попробуйте позже.');
                            return;
                        }
                        log('[DEBUG] Ждем 5 секунд...');
                        var start = Date.now();
                        while (Date.now() - start < 5000) {}
                        continue;
                    }

                    if (typeof tempRes === 'object' && tempRes !== null) {
                        if (tempRes.error) {
                            log('[ERROR] Получена ошибка от сервиса: ' + toPrettyString(tempRes));
                            $reactions.answer('Ошибка при обработке. Попробуйте ещё раз.');
                            return;
                        }
                        userResponse = tempRes.userResponse;
                        data = tempRes.data;
                        status = tempRes.status;
                    } else {
                        log('[ERROR] Неожиданный формат ответа: ' + toPrettyString(tempRes));
                        userResponse = String(tempRes);
                        data = null;
                        status = null;
                    }

                    log('[DEBUG] 🧠 openAI = ' + toPrettyString(tempRes));
                    log('[DEBUG] 🧠 userResponse = ' + toPrettyString(userResponse));
                    log('[DEBUG] 🧠 data = ' + toPrettyString(data));
                    log('[DEBUG] 🧠 status = ' + toPrettyString(status));

                    if (userResponse && typeof userResponse === 'string') {
                        $reactions.answer(userResponse);
                    }
                    $session.messages.push({ role: "assistant", content: userResponse });

                    var result = gerUserByBitrix($session.id_telegram);
                    if (result && result.length > 0) {
                        var bitrix_id = result[0].ID;
                        var leadManagerInstance = new leadManager($context);

                        if (status) {
                            leadManagerInstance.updateLeadFields(bitrix_id, { STATUS_ID: status });
                        }

                        if (data) {
                            var bitrixFields = {};
                            if (data.themes && data.themes.length > 0) {
                                bitrixFields.userThemes = String(data.themes[0].id);
                            }
                            if (data.micro_themes && data.micro_themes.length > 0) {
                                bitrixFields.userMicroThemes = String(data.micro_themes[0].id);
                            }
                            if (data.urgencyLevels && data.urgencyLevels.length > 0) {
                                bitrixFields.userUrgencyLevels = String(data.urgencyLevels[0].id);
                            } else if (data.additional && data.additional.urgency) {
                                var urgencyMap = { "низкая": "106", "средняя": "104", "высокая": "102" };
                                var urgencyId = urgencyMap[data.additional.urgency.toLowerCase()];
                                if (urgencyId) bitrixFields.userUrgencyLevels = urgencyId;
                            }
                            if (data.client_name) {
                                bitrixFields.userName = data.client_name;
                            }
                            if (data.client_phone) {
                                bitrixFields.userPhone = data.client_phone;
                            }

                            if (Object.keys(bitrixFields).length > 0) {
                                try {
                                    var updateResult = leadManagerInstance.updateLeadFields(bitrix_id, bitrixFields);
                                    log('[DEBUG] Результат обновления: ' + toPrettyString(updateResult));

                                    var promt_get_result = prompt.promt_get_result;
                                    var brainRes = askBrain(promt_get_result, $session.messages);

                                    if (brainRes && brainRes.userResponse) {
                                        leadManagerInstance.updateLeadFields(bitrix_id, {
                                            UF_CRM_1750364958: brainRes.userResponse
                                        });
                                        log('[DEBUG] Обновлено UF_CRM_1750364958: ' + brainRes.userResponse);
                                    } else {
                                        log('[ERROR] Нет userResponse: ' + toPrettyString(brainRes));
                                    }
                                } catch (bitrixError) {
                                    log('[ERROR] Ошибка при обновлении: ' + toPrettyString(bitrixError));
                                }
                            } else {
                                log('[DEBUG] Нет данных для обновления в Битриксе');
                            }
                        }
                    } else {
                        log('[ERROR] Лид не найден в Битриксе');
                    }

                    break;
                } catch (error) {
                    currentRetry++;
                    log('[ERROR] askBrain исключение, попытка ' + currentRetry + ' из ' + maxRetries + ': ' + toPrettyString(error));
                    if (currentRetry >= maxRetries) {
                        $reactions.answer('Извините, ошибка. Попробуйте позже.');
                        return;
                    }
                    log('[DEBUG] Ждем 5 секунд...');
                    var start = Date.now();
                    while (Date.now() - start < 5000) {}
                }
            }

    state: Stop
        q!: 2
        script:
            $jsapi.stopSession();
        a: Сессия завершена. Контекст очищен.

    state: 3
        q!: 3
        script:
            $reactions.answer(JSON.stringify($request));
require: requirements.sc

init:
    $global.$ = {
        __noSuchProperty__: function(property) {
            return $jsapi.context()[property];
        }
    };

    bind("preMatch", function($context) {
        var $ = $context;
        $.session.AUTH = $.injector.auth;
        $.session.API = $.injector.api;
    });

theme: /

    state: StartFirst

        q!: $regex<test>
        q!: test
        q!: 1
        a: Здравствуйте!. Возник вопрос?
        script:
            try {
                log('[+++] 🧠🧠🧠 request = ' + toPrettyString($request));

                // Определяем канал пользователя
                var userChannel = $request.rawRequest.chatType; // telegram или whatsapp

                if (userChannel == "telegram") {
                    // Получаем пользователя из Битрикса
                    var id_telegram = $request.userFrom.id;
                    $session.id_telegram = id_telegram;
                    var username = ($request.rawRequest &&
                                    $request.rawRequest.contact &&
                                    $request.rawRequest.contact.username &&
                                    $request.rawRequest.contact.username.trim() !== "")
                                   ? $request.rawRequest.contact.username
                                   : "";

                    // Проверяем, есть ли пользователь в Битриксе
                    var result = gerUserByBitrix(id_telegram);

                    if (_.isEmpty(result)) {
                        var leadManagerInstance = new leadManager($context);
                        leadManagerInstance.createLead({
                            channel: "TELEGRAM1",
                            id_telegram: id_telegram,
                            userName: username
                        });
                    } else {
                        // TODO: логика обработки существующего лида
                    }

                } else if (userChannel == "whatsapp") {
                    // TODO: логика для WhatsApp
                } else {
                    // TODO: другое подключение
                }

                var themes = getBitrixUserFields(); // Получаем тематику и микротематику
                var themes_text = extractThemesFromBitrix(themes);
                $session.themes_text = themes_text;

                $session.messages = [
                    {
                        role: "assistant",
                        content: "Здравствуйте!. Возник вопрос?"
                    }
                ];
            } catch (e) {
                $reactions.answer(toPrettyString(e.stack));
            }

    state: NoMatch
        event!: noMatch
        script:
            var userChannel = $request.rawRequest.chatType; // telegram или whatsapp

            if (userChannel == "telegram") {
                var id_telegram = $request.userFrom.id;
                $session.id_telegram = id_telegram;
                var username = ($request.rawRequest &&
                                $request.rawRequest.contact &&
                                $request.rawRequest.contact.username &&
                                $request.rawRequest.contact.username.trim() !== "")
                               ? $request.rawRequest.contact.username
                               : "";
            }

            // История переписки
            if (!$session.messages) {
                $session.messages = [];
            }
            $session.messages.push({
                role: "user",
                content: $request.query
            });

            // Тематики для промта
            var themes_data = "";
            if ($session.themes_text) {
                themes_data = JSON.stringify($session.themes_text);
            }

            var full_prompt = prompt.prompt_tg
                + "\n\n📋 ДОСТУПНЫЕ ТЕМАТИКИ И МИКРОТЕМАТИКИ:\n"
                + themes_data
                + "\n\nСтатусы:\n"
                + prompt.status;

            var maxRetries = 5;
            var currentRetry = 0;
            var tempRes, userResponse, data, status;

            while (currentRetry < maxRetries) {
                try {
                    tempRes = askBrain(full_prompt, $session.messages);

                    if (!tempRes) {
                        currentRetry++;
                        log('[ERROR] askBrain вернул пустой результат, попытка ' + currentRetry + ' из ' + maxRetries);
                        if (currentRetry >= maxRetries) {
                            $reactions.answer('Извините, не удалось получить ответ от ИИ. Попробуйте позже.');
                            return;
                        }
                        log('[DEBUG] Ждем 5 секунд...');
                        var start = Date.now();
                        while (Date.now() - start < 5000) {}
                        continue;
                    }

                    if (tempRes.error) {
                        currentRetry++;
                        log('[ERROR] askBrain вернул ошибку, попытка ' + currentRetry + ' из ' + maxRetries + ': ' + toPrettyString(tempRes));
                        if (currentRetry >= maxRetries) {
                            $reactions.answer('Извините, техническая ошибка. Попробуйте позже.');
                            return;
                        }
                        log('[DEBUG] Ждем 5 секунд...');
                        var start = Date.now();
                        while (Date.now() - start < 5000) {}
                        continue;
                    }

                    if (typeof tempRes === 'object' && tempRes !== null) {
                        if (tempRes.error) {
                            log('[ERROR] Получена ошибка от сервиса: ' + toPrettyString(tempRes));
                            $reactions.answer('Ошибка при обработке. Попробуйте ещё раз.');
                            return;
                        }
                        userResponse = tempRes.userResponse;
                        data = tempRes.data;
                        status = tempRes.status;
                    } else {
                        log('[ERROR] Неожиданный формат ответа: ' + toPrettyString(tempRes));
                        userResponse = String(tempRes);
                        data = null;
                        status = null;
                    }

                    log('[DEBUG] 🧠 openAI = ' + toPrettyString(tempRes));
                    log('[DEBUG] 🧠 userResponse = ' + toPrettyString(userResponse));
                    log('[DEBUG] 🧠 data = ' + toPrettyString(data));
                    log('[DEBUG] 🧠 status = ' + toPrettyString(status));

                    if (userResponse && typeof userResponse === 'string') {
                        $reactions.answer(userResponse);
                    }
                    $session.messages.push({ role: "assistant", content: userResponse });

                    var result = gerUserByBitrix($session.id_telegram);
                    if (result && result.length > 0) {
                        var bitrix_id = result[0].ID;
                        var leadManagerInstance = new leadManager($context);

                        if (status) {
                            leadManagerInstance.updateLeadFields(bitrix_id, { STATUS_ID: status });
                        }

                        if (data) {
                            var bitrixFields = {};
                            if (data.themes && data.themes.length > 0) {
                                bitrixFields.userThemes = String(data.themes[0].id);
                            }
                            if (data.micro_themes && data.micro_themes.length > 0) {
                                bitrixFields.userMicroThemes = String(data.micro_themes[0].id);
                            }
                            if (data.urgencyLevels && data.urgencyLevels.length > 0) {
                                bitrixFields.userUrgencyLevels = String(data.urgencyLevels[0].id);
                            } else if (data.additional && data.additional.urgency) {
                                var urgencyMap = { "низкая": "106", "средняя": "104", "высокая": "102" };
                                var urgencyId = urgencyMap[data.additional.urgency.toLowerCase()];
                                if (urgencyId) bitrixFields.userUrgencyLevels = urgencyId;
                            }
                            if (data.client_name) {
                                bitrixFields.userName = data.client_name;
                            }
                            if (data.client_phone) {
                                bitrixFields.userPhone = data.client_phone;
                            }

                            if (Object.keys(bitrixFields).length > 0) {
                                try {
                                    var updateResult = leadManagerInstance.updateLeadFields(bitrix_id, bitrixFields);
                                    log('[DEBUG] Результат обновления: ' + toPrettyString(updateResult));

                                    var promt_get_result = prompt.promt_get_result;
                                    var brainRes = askBrain(promt_get_result, $session.messages);

                                    if (brainRes && brainRes.userResponse) {
                                        leadManagerInstance.updateLeadFields(bitrix_id, {
                                            UF_CRM_1750364958: brainRes.userResponse
                                        });
                                        log('[DEBUG] Обновлено UF_CRM_1750364958: ' + brainRes.userResponse);
                                    } else {
                                        log('[ERROR] Нет userResponse: ' + toPrettyString(brainRes));
                                    }
                                } catch (bitrixError) {
                                    log('[ERROR] Ошибка при обновлении: ' + toPrettyString(bitrixError));
                                }
                            } else {
                                log('[DEBUG] Нет данных для обновления в Битриксе');
                            }
                        }
                    } else {
                        log('[ERROR] Лид не найден в Битриксе');
                    }

                    break;
                } catch (error) {
                    currentRetry++;
                    log('[ERROR] askBrain исключение, попытка ' + currentRetry + ' из ' + maxRetries + ': ' + toPrettyString(error));
                    if (currentRetry >= maxRetries) {
                        $reactions.answer('Извините, ошибка. Попробуйте позже.');
                        return;
                    }
                    log('[DEBUG] Ждем 5 секунд...');
                    var start = Date.now();
                    while (Date.now() - start < 5000) {}
                }
            }

    state: Stop
        q!: 2
        script:
            $jsapi.stopSession();
        a: Сессия завершена. Контекст очищен.

    state: 3
        q!: 3
        script:
            $reactions.answer(JSON.stringify($request));
