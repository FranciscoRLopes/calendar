# Plano de Testes - Assignment 2 (VVS 2025/2026)

[cite_start]Este ficheiro acompanha o progresso da implementação dos testes para a aplicação **Calendar**, conforme os requisitos do Assignment 2[cite: 3].


Atenção : No enunciado é dito que pode existir bugs e devemos encontra los.
## Checklist de Testes

- [x] **1. Testes de Unidade (Business Logic)**
    - **Target:** `src/main/java/com/example/meetings/service/`
    - **Objetivo:** Validar regras de negócio (criação de reuniões, convites, estados).
    - **Técnica:** Utilizar JUnit 5 e Mockito para fazer mock dos repositórios.

- [ ] **2. Testes de Integração (3rd Party Sources)**
    - **Target:** `src/main/java/com/example/meetings/discover/`
    - **Objetivo:** Validar a comunicação com APIs externas (Ticketmaster, SeatGeek, Agenda Cultural).
    - [cite_start]**Técnica:** Utilizar mock servers (ex: WireMock) para simular respostas e falhas de rede[cite: 11].

- [ ] **3. Testes de Integração (REST API)**
    - **Target:** `src/main/java/com/example/meetings/controller/`
    - **Objetivo:** Validar os *endpoints* da API.
    - [cite_start]**Técnica:** Utilizar `MockMvc` para testar controllers isoladamente[cite: 12].

- [ ] **4. Testes de Integração (Base de Dados)**
    - **Target:** `src/main/java/com/example/meetings/repository/`
    - **Objetivo:** Validar queries e persistência JPA.
    - [cite_start]**Técnica:** Utilizar `@DataJpaTest` com a base de dados H2[cite: 13].

- [ ] **5. Testes End-to-End (E2E)**
    - **Objetivo:** Validar fluxos completos da aplicação.
    - [cite_start]**Técnica:** Utilizar Selenium com uma base de dados de testes dedicada[cite: 14].

## Notas de Configuração
- [ ] [cite_start]Configurar CI (Continuous Integration) no repositório[cite: 15].
- [ ] [cite_start]Registar todas as modificações no SUT (System Under Test) para o relatório final[cite: 17].

## Registo de Atividades
* *Data - Descrição da tarefa realizada*