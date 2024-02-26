# OneNet Backend

## Run

1. To deploy locally for developing this spring repo, get the required services, Keycloak and db as docker containers running by:

    ```
    docker-compose up -d
    ```

    NOTE: The local build of OneNet runs using the default values from `application.yml`, it is not affected by `.env`.


2. Edit `src/main/resources/application.yml`.

    Change `elastic-url` and `elasticsearch.uris` to point to your ElasticSearch instance.

3. Then run the OneNet backend from Intellij or JAR file, or by maven wrapper (example below):

    ```
    ./mvnw clean install spring-boot:run
    ```

    `mvnw` needs to have execute permissions:

    ```
    chmod 777 mvnw
    ```


# Build docker image

```
./mvnw clean install spring-boot:run
docker build . -t onenet-dashboard-backend-image
```

### Services
#### Keycloak
Access the keycloak page at:

http://localhost:8090/auth/admin

#### Swagger

Swagger can be accessed by:

http://localhost:8080/api/v1/swagger-ui.html

http://localhost:8080/api/v1/v3/api-docs

#### Health check and metrics

General health: http://localhost:8080/api/v1/actuator/health

Keycloak health check: http://localhost:8080/api/v1/actuator/health/KeycloakHealth

## Troubleshooting

If you run by a message "keycloak user already exists" when the container is starting OR the keycloak container does not start at all, you have two choices:
- In [docker-compose.yml](./docker-compose.yml) comment out the `KEYCLOAK_USER` and `KEYCLOAK_PASSWORD` lines like so:

    ```
    environment:
    # - KEYCLOAK_USER=${KEYCLOAK_ADMIN_USERNAME}
    # - KEYCLOAK_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
    ```

    Then start the container. It should start normally. Finally, revert the changes to `docker-compose.yml`.

OR

- Run `docker compose down -v` destroying the volumes. The next time up, the keycloak configuration will be imported from scratch via the \*-realm.json that is used on the import. NOTE this will destroy all database entries and data.

This is a common problem with Keycloak and happens if the container is stopped before initialized completely, or forcefully stopped.
