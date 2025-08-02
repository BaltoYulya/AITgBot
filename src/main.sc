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


    state: StartChat
        event!: noMatch
        q!: $regex</test>
        q!: test
        q!: 1
        script:



            if(!$session.firstMessage) {
                var start = Date.now();
                while (Date.now() - start < 5000) { }
                $session.firstMessage = true;
            }


            var userChannel = $request.rawRequest.chatType; // тип канала telegram или whatsapp
            var cus = $request.userFrom.id;

            var cus = '79000546917'; // TODO: удалить
            var userChannel = 'whatsapp'; // TODO: удалить

            if (userChannel == "telegram") {
                var butrixLeadId = getUserByBitrix(cus, "telegram");
            } else if (userChannel == "whatsapp") {
                var butrixLeadId = getUserByBitrix(cus, "whatsapp");
            }

            // TODO: Сделать проверку на !_.isNull(butrixLeadId)

            var promt = getSystemPrompt(userChannel); // Собираем системный промпт

            if (!$session.messages) $session.messages = [];
            $session.messages.push({
                "role": "user",
                "content": $request.query
            });



            // Максимальное количество попыток для вызова OpenAI
            var maxRetries = 5;
            var currentRetry = 0;
            var tempRes, userResponse, data, status;


            while (currentRetry < maxRetries) {
                try {
                    tempRes = askBrain(promt, $session.messages);
                    
                    // Проверяем на ошибки
                    if (!tempRes) {
                        currentRetry++;
                        log('[ +++ ERROR] askBrain вернул пустой результат, попытка ' + currentRetry + ' из ' + maxRetries);
                        
                        if (currentRetry >= maxRetries) {
                            $reactions.answer('Извините, походу подвис интернет. я не получил вашего сообщения');
                            return;
                        }
                
                
                        // Задержка 5 секунд перед повторной попыткой
                        log('[ +++ DEBUG] Ждем 5 секунд перед повторной попыткой...');
                        var start = Date.now();
                        while (Date.now() - start < 5000) { }
                        continue;
                    }

                    // Проверяем на ошибку в ответе
                    if (tempRes.error) {
                        currentRetry++;
                        log('[+++ ERROR] askBrain вернул ошибку, попытка ' + currentRetry + ' из ' + maxRetries + ': ' + toPrettyString(tempRes));
                        
                        if (currentRetry >= maxRetries) {
                            $reactions.answer('Извините, произошла техническая ошибка. Попробуйте повторить запрос позже.');
                            return;
                        }
                        
                        // Задержка 5 секунд перед повторной попыткой
                        log('[+++ DEBUG] Ждем 5 секунд перед повторной попыткой...');
                        var start = Date.now();
                        while (Date.now() - start < 5000) {}
                        continue;
                    }

                    // Новый сервис всегда возвращает JSON объект
                    if (typeof tempRes === 'object' && tempRes !== null) {
                        // Проверяем, есть ли ошибка в ответе
                        if (tempRes.error) {
                            log('[+++ ERROR] Получена ошибка от сервиса: ' + toPrettyString(tempRes));
                            $reactions.answer('Извините, произошла ошибка при обработке запроса. Попробуйте ещё раз.');
                            return;
                        }
                        
                        // Извлекаем данные из объекта
                        userResponse = tempRes.userResponse;
                        data = tempRes.data;
                        status = tempRes.status;
                        
                    } else {
                        // Неожиданный формат - логируем и используем как fallback
                        log('[+++ ERROR] Неожиданный формат ответа от askBrain: ' + toPrettyString(tempRes));
                        userResponse = String(tempRes);
                        data = null;
                        status = null;
                    }

                    // Логируем данные для отладки
                    log('[DEBUG] 🧠 То что пришло от openAI = ' + toPrettyString(tempRes));
                    log('[DEBUG] 🧠 userResponse = ' + toPrettyString(userResponse));
                    log('[DEBUG] 🧠 data = ' + toPrettyString(data));
                    log('[DEBUG] 🧠 status = ' + toPrettyString(status));

                    // Отправляем сообщение пользователю
                    if (userResponse && typeof userResponse === 'string') {
                        $reactions.answer(userResponse);
                    }

                    // Добавляем ответ ассистента в историю сообщений
                    $session.messages.push({
                        "role": "assistant",
                        "content": userResponse
                    });

  
                    var leadManagerInstance = new leadManager($context);

                    // Обновляем статус в битриксе
                    if (status) {
                        leadManagerInstance.updateLeadFields(butrixLeadId, {
                            "STATUS_ID": status
                        });
                    }

                    // Обновляем Тематики и микротематики + статусы
                    if (data) {
                        var bitrixFields = {};
                        
                        log('[DEBUG] data.themes = ' + toPrettyString(data.themes));
                        log('[DEBUG] data.micro_themes = ' + toPrettyString(data.micro_themes));
                        
                        if (data.themes && data.themes.length > 0) {
                            bitrixFields.userThemes = String(data.themes[0].id);
                            log('[DEBUG] Установлена тематика: ' + data.themes[0].id);
                        }
                        
                        if (data.micro_themes && data.micro_themes.length > 0) {
                            bitrixFields.userMicroThemes = String(data.micro_themes[0].id);
                            log('[DEBUG] Установлена микротематика: ' + data.micro_themes[0].id);
                        }
                        
                        if (data.urgencyLevels && data.urgencyLevels.length > 0) {
                            bitrixFields.userUrgencyLevels = String(data.urgencyLevels[0].id);
                            log('[DEBUG] Установлена срочность: ' + data.urgencyLevels[0].id);
                        } else if (data.additional && data.additional.urgency) {
                            var urgencyMap = {
                                "низкая": "106",
                                "средняя": "104", 
                                "высокая": "102"
                            };
                            var urgencyId = urgencyMap[data.additional.urgency.toLowerCase()];
                            if (urgencyId) {
                                bitrixFields.userUrgencyLevels = urgencyId;
                                log('[DEBUG] Установлена срочность: ' + urgencyId);
                            }
                        }
                        
                        if (data.client_name) {
                            bitrixFields.userName = data.client_name;
                        }
                        
                        if (data.client_phone) {
                            bitrixFields.userPhone = data.client_phone;
                        }

                        // Отправляем обновление в Битрикс
                        if (Object.keys(bitrixFields).length > 0) {
                            try {
                                var updateResult = leadManagerInstance.updateLeadFields(butrixLeadId, bitrixFields);
                                log('[DEBUG] Результат обновления Битрикс: ' + toPrettyString(updateResult));

                                // Получаем promt_get_result из словаря промтов
                                var promt_get_result = prompt.promt_get_result;
                                var messages = $session.messages;

                                // Вызываем askBrain с нужным промтом и историей сообщений
                                var brainRes = askBrain(promt_get_result, messages);

                                // Проверяем, что получили корректный ответ
                                if (brainRes && brainRes.userResponse) {
                                    // Обновляем в Битриксе поле UF_CRM_1750364958
                                    leadManagerInstance.updateLeadFields(butrixLeadId, {
                                        "UF_CRM_1750364958": brainRes.userResponse // текст запроса
                                    });
                                    log('[+++ DEBUG] Обновлено UF_CRM_1750364958 в Битриксе: ' + brainRes.userResponse);
                                } else {
                                    log('[+++ ERROR] Не удалось получить userResponse из askBrain для promt_get_result: ' + toPrettyString(brainRes));
                                }
                            } catch (bitrixError) {
                                log('[ERROR] Ошибка при обновлении Битрикс: ' + toPrettyString(bitrixError));
                            }
                        } else {
                            log('[DEBUG] Нет данных для обновления в Битриксе');
                        }
                    }

                    // Успешно получили и обработали ответ, выходим из цикла
                    break;

                } catch (error) {
                    currentRetry++;
                    log('[ERROR] askBrain выбросил исключение, попытка ' + currentRetry + ' из ' + maxRetries + ': ' + toPrettyString(error));
                    
                    if (currentRetry >= maxRetries) {
                        $reactions.answer('Извините, произошла техническая ошибка. Попробуйте повторить запрос позже.');
                        return;
                    }
                    
                    // Задержка 5 секунд перед повторной попыткой
                    log('[DEBUG] Ждем 5 секунд перед повторной попыткой...');
                    var start = Date.now();
                    while (Date.now() - start < 5000) {
                        // Ждем 5 секунд
                    }
      
                }
            }

    state: Stop
        q!: 2
        script: $jsapi.stopSession();
        a: Сессия завершена. Контекст очищен.

    state: 3
        q!: 3
        script: 
            var result = getUserByBitrix('79833834615', "whatsapp");
            log('[<insert>] 🧠🧠🧠1 result = ' + toPrettyString(result));
            $reactions.answer(JSON.stringify(result));
            var result = getUserByBitrix('986859574', "telegram");
            log('[+++] 🧠🧠🧠2 result = ' + toPrettyString(result));
            $reactions.answer(JSON.stringify(result));


    # TODO: Нужно на будущее хранить историю всех переписок для дальнейшего анализа и оценки
    # TODO: 


    state: 4\
        q!: 4
        script: 
            var cus = '79000546917'; // TODO: удалить
            var userChannel = 'whatsapp'; // TODO: удалить

            var result = getUserByBitrix(cus, userChannel);
            $reactions.answer(JSON.stringify(result));






