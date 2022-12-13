# NOSQL база данных
Ветка `master`:
[![Tests](https://github.com/VladislavFetisov/NoSQL_DB/actions/workflows/tests.yml/badge.svg?branch=master)](https://github.com/VladislavFetisov/NoSQL_DB/actions/workflows/tests.yml)

Ветка `develop`:
[![Tests](https://github.com/VladislavFetisov/NoSQL_DB/actions/workflows/tests.yml/badge.svg?branch=develop)](https://github.com/VladislavFetisov/NoSQL_DB/actions/workflows/tests.yml)

## Описание
## Описание 
База данных, написанная на JAVA для хранения сроковых значений и выдача их по ключу
## Запуск
* Необходима 17 версия JRE
1. Склонировать репозиторий
```
git clone https://github.com/VladislavFetisov/NoSQL_DB.git
```
2. Запустить через gradle
```
./gradlew run
```
## Запуск c помощью Docker
1. Скачать [image](https://hub.docker.com/r/kuakaka/no_sql) проекта
```
docker pull kuakaka/no_sql
```
2. Запустить Docker container
```
docker run -p 8080:8080 kuakaka/no_sql 
```
## Интерфейс
* HTTP `GET /v0/status` -- проверить статус сервиса`. Возвращает `200 OK`.
* HTTP `GET /v0/entity?id=<ID>` -- получить данные по ключу `<ID>`. Возвращает `200 OK` и данные или `404 Not Found`.
* HTTP `PUT /v0/entity?id=<ID>` -- создать/перезаписать (upsert) данные по ключу `<ID>`. Возвращает `201 Created`.
* HTTP `DELETE /v0/entity?id=<ID>` -- удалить данные по ключу `<ID>`. Возвращает `202 Accepted`.
## Примеры работы
1. После старта сервиса пробуем получить данные по отсутствующему ключу:
```
GET /v0/entity?id=1
```
Ответ:
```
Status code=404
Body=empty
```
2. Положим недостающее значение
```
PUT /v0/entity?id=1
Body=SOME value
```
Ответ:
```
Status code=201
```
3. Получим данные по существующему ключу
```
GET /v0/entity?id=1
```
Ответ:
```
Status code=200
Body=SOME value
```
4. Удалим данные по существующему ключу
```
DELETE /v0/entity?id=1
```
Ответ:
```
Status code=202
```
5. Получим данные по удаленному ключу
```
GET /v0/entity?id=1
```
Ответ:
```
Status code=404
Body=empty
```