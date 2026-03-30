# Online Exam System (在线考试系统)

## Project Overview

This is an online examination system designed to provide a centralized, frontend-backend separated platform for educational testing and assessment. 

**Core Subsystems:**
1.  **User & Permission Management:** Role-based access control (Admin, Teacher, Student).
2.  **Question Bank & Exam Paper Management:** CRUD for multiple question types and smart exam generation.
3.  **Exam Process Monitoring:** Real-time exam session management, countdowns, and anti-cheating measures.
4.  **Grading & Data Visualization:** Automated grading for objective questions and visual analytics using ECharts.

**Tech Stack:**
*   **Backend:** Java 21, Spring Boot 3 (Port: `16730`), Spring Security, MyBatis-Plus, JWT.
*   **Frontend:** Vue 3 (Composition API), Vite (Port: `5173`), Element-Plus, ECharts, Pinia, Vue Router.
*   **Database:** MySQL 8.4 (Default user/pass: `root` / `962464`, DB: `exam_mvp`).
*   **Cache:** Redis (Default pass: `962464`).
*   **Storage:** MinIO (for question images).

## Directory Structure

*   `/src/main/java/com/ekusys/exam/`: Backend Java source code (organized by domain: `admin`, `analytics`, `auth`, `common`, `exam`, `grading`, `paper`, `question`, `teacher`).
*   `/src/main/resources/`: Backend configurations (`application.yaml`), database scripts (`schema.sql`, `data.sql`), and MyBatis mapper XMLs.
*   `/src/main/resources/frontend/`: Frontend Vue 3 application root.
*   `/pom.xml`: Maven build configuration.

## Building and Running

### Backend (Spring Boot)

The backend is a standard Maven project.

1.  **Prerequisites:** Ensure MySQL, Redis, and MinIO are running with the credentials specified in `application.yaml`.
2.  **Build:**
    ```bash
    ./mvnw clean package -DskipTests
    ```
3.  **Run (Development):**
    ```bash
    ./mvnw spring-boot:run
    ```

### Frontend (Vue 3 / Vite)

The frontend is located within the `src/main/resources/frontend` directory.

1.  **Navigate to Frontend Directory:**
    ```bash
    cd src/main/resources/frontend
    ```
2.  **Install Dependencies:**
    ```bash
    npm install
    # or pnpm install (as recommended in the project planning document)
    ```
3.  **Run Development Server:**
    ```bash
    npm run dev
    ```
4.  **Build for Production:**
    ```bash
    npm run build
    ```
    *(Note: The Maven build is configured to exclude the `frontend/node_modules` and `frontend/dist` directories from the final Spring Boot JAR.)*

## Development Conventions

*   **Architecture:** The project strictly follows a frontend-backend separation architecture. The frontend communicates with the backend via RESTful APIs using Axios.
*   **Code Style:**
    *   **Backend:** Standard Java Spring Boot conventions. Uses Lombok for boilerplate reduction and MyBatis-Plus for simplified database operations.
    *   **Frontend:** Vue 3 Composition API style. UI components are primarily sourced from Element-Plus. ECharts is used for complex data visualization.
*   **Database:** Ensure your local MySQL instance matches the connection strings in `src/main/resources/application.yaml`. Default credentials are `root` / `962464`.
*   **Testing:** Automated tests are located in `src/test/java/`. Use `./mvnw test` to run them.


# Gemini Project Rules

## 语言规范 (Language)
- **始终使用中文**进行对话、解释代码逻辑和技术建议。
- 保持专业术语为英文（例如：Spring Boot, Vue, Middleware, JWT, Axios）。
- 代码注释请使用中文。

