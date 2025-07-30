require: requirements.sc

init:
\$global.\$ = {
**noSuchProperty**: function(property) {
return \$jsapi.context()\[property];
}
};

```
bind("preMatch", function($context) {
    var $ = $context;
    $.session.AUTH = $.injector.auth;
    $.session.API = $.injector.api;
});
```

theme: /

```
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

            // Сохраняем тематики
            var themes = getBitrixUserFields();
            var themes_text = extractThemesFromBitrix(themes);
            $session.themes_text = themes_text;

            // Начало истории переписки
            $session.messages = [
                { role: "assistant", content: "Здравствуйте!. Возник вопрос?" }
            ];

        } catch (e) {
            $reactions.answer(toPrettyString(e.stack));
        }

state: NoMatch
    event!: noMatch
    script:
        // Добавляем сообщение пользователя в историю
        if (!$session.messages) {
            $session.messages = [];
        }
        $session.messages.push({ role: "user", content: $request.query });

        // Тематики для промта
        var themes_data = $session.themes_text ? JSON.stringify($session.themes_text) : "";
        var full_prompt = prompt.prompt_tg
            + "\n\n📋 ДОСТУПНЫЕ ТЕМАТИКИ И МИКРОТЕМАТИКИ:\n" + themes_data
            + "\n\nСтатусы:\n" + prompt.status;

        var maxRetries = 5;
        var currentRetry = 0;
        while (currentRetry < maxRetries) {
            try {
                var tempRes = askBrain(full_prompt, $session.messages);
                if (!tempRes || tempRes.error) {
                    currentRetry++;
                    log('[ERROR] askBrain ошибка или пустой результат, попытка ' + currentRetry);
                    if (currentRetry >= maxRetries) {
                        $reactions.answer('Извините, не удалось получить ответ. Попробуйте позже.');
                        return;
                    }
                    // Задержка
                    var start = Date.now(); while (Date.now() - start < 5000) {}
                    continue;
                }

                var userResponse = tempRes.userResponse;
                var data = tempRes.data;
                var status = tempRes.status;

                $reactions.answer(userResponse);
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
                        if (data.themes && data.themes.length) bitrixFields.userThemes = String(data.themes[0].id);
                        if (data.micro_themes && data.micro_themes.length) bitrixFields.userMicroThemes = String(data.micro_themes[0].id);
                        if (data.urgencyLevels && data.urgencyLevels.length) {
                            bitrixFields.userUrgencyLevels = String(data.urgencyLevels[0].id);
                        }
                        if (data.client_name) bitrixFields.userName = data.client_name;
                        if (data.client_phone) bitrixFields.userPhone = data.client_phone;

                        if (Object.keys(bitrixFields).length) {
                            try {
                                leadManagerInstance.updateLeadFields(bitrix_id, bitrixFields);
                                var brainRes = askBrain(prompt.promt_get_result, $session.messages);
                                if (brainRes && brainRes.userResponse) {
                                    leadManagerInstance.updateLeadFields(bitrix_id, { UF_CRM_1750364958: brainRes.userResponse });
                                }
                            } catch (bitrixError) {
                                log('[ERROR] Ошибка при обновлении лида: ' + toPrettyString(bitrixError));
                            }
                        }
                    }
                }

                break;
            } catch (error) {
                currentRetry++;
                if (currentRetry >= maxRetries) { $reactions.answer('Произошла ошибка.'); return; }
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
```
require: requirements.sc

init:
\$global.\$ = {
**noSuchProperty**: function(property) {
return \$jsapi.context()\[property];
}
};

```
bind("preMatch", function($context) {
    var $ = $context;
    $.session.AUTH = $.injector.auth;
    $.session.API = $.injector.api;
});
```

theme: /

```
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

            // Сохраняем тематики
            var themes = getBitrixUserFields();
            var themes_text = extractThemesFromBitrix(themes);
            $session.themes_text = themes_text;

            // Начало истории переписки
            $session.messages = [
                { role: "assistant", content: "Здравствуйте!. Возник вопрос?" }
            ];

        } catch (e) {
            $reactions.answer(toPrettyString(e.stack));
        }

state: NoMatch
    event!: noMatch
    script:
        // Добавляем сообщение пользователя в историю
        if (!$session.messages) {
            $session.messages = [];
        }
        $session.messages.push({ role: "user", content: $request.query });

        // Тематики для промта
        var themes_data = $session.themes_text ? JSON.stringify($session.themes_text) : "";
        var full_prompt = prompt.prompt_tg
            + "\n\n📋 ДОСТУПНЫЕ ТЕМАТИКИ И МИКРОТЕМАТИКИ:\n" + themes_data
            + "\n\nСтатусы:\n" + prompt.status;

        var maxRetries = 5;
        var currentRetry = 0;
        while (currentRetry < maxRetries) {
            try {
                var tempRes = askBrain(full_prompt, $session.messages);
                if (!tempRes || tempRes.error) {
                    currentRetry++;
                    log('[ERROR] askBrain ошибка или пустой результат, попытка ' + currentRetry);
                    if (currentRetry >= maxRetries) {
                        $reactions.answer('Извините, не удалось получить ответ. Попробуйте позже.');
                        return;
                    }
                    // Задержка
                    var start = Date.now(); while (Date.now() - start < 5000) {}
                    continue;
                }

                var userResponse = tempRes.userResponse;
                var data = tempRes.data;
                var status = tempRes.status;

                $reactions.answer(userResponse);
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
                        if (data.themes && data.themes.length) bitrixFields.userThemes = String(data.themes[0].id);
                        if (data.micro_themes && data.micro_themes.length) bitrixFields.userMicroThemes = String(data.micro_themes[0].id);
                        if (data.urgencyLevels && data.urgencyLevels.length) {
                            bitrixFields.userUrgencyLevels = String(data.urgencyLevels[0].id);
                        }
                        if (data.client_name) bitrixFields.userName = data.client_name;
                        if (data.client_phone) bitrixFields.userPhone = data.client_phone;

                        if (Object.keys(bitrixFields).length) {
                            try {
                                leadManagerInstance.updateLeadFields(bitrix_id, bitrixFields);
                                var brainRes = askBrain(prompt.promt_get_result, $session.messages);
                                if (brainRes && brainRes.userResponse) {
                                    leadManagerInstance.updateLeadFields(bitrix_id, { UF_CRM_1750364958: brainRes.userResponse });
                                }
                            } catch (bitrixError) {
                                log('[ERROR] Ошибка при обновлении лида: ' + toPrettyString(bitrixError));
                            }
                        }
                    }
                }

                break;
            } catch (error) {
                currentRetry++;
                if (currentRetry >= maxRetries) { $reactions.answer('Произошла ошибка.'); return; }
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
```
