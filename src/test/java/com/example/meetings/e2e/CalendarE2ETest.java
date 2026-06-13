package com.example.meetings.e2e;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for the Calendar application using Selenium WebDriver.
 *
 * These tests exercise complete user flows through the browser, verifying
 * that controllers, templates, services, and the database work together
 * correctly. Each test interacts only with the UI — no internal Spring beans
 * are accessed — which reflects the perspective of a real end user.
 *
 * The application is started on a random port via @SpringBootTest with the
 * "test" profile, which switches the datasource to an isolated H2 database
 * defined in application-test.properties. Chrome runs in headless mode so
 * the tests can execute in CI without a display.
 *
 * ⚠️  SUT modification required: add application-test.properties (see below)
 * and the selenium/webdrivermanager dependencies to pom.xml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarE2ETest {

    @LocalServerPort
    private int port;

    private static WebDriver driver;
    private static WebDriverWait wait;

    // Unique usernames per test run to avoid state leakage between runs
    // (the H2 database resets between SpringBootTest contexts, but not within one).
    private static final String USER_A      = "alice_e2e";
    private static final String USER_B      = "bob_e2e";
    private static final String PASSWORD    = "Test@1234";
    private static final String EMAIL_A     = "alice_e2e@example.com";
    private static final String EMAIL_B     = "bob_e2e@example.com";

    private static final DateTimeFormatter DT_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ── WebDriver lifecycle ─────────────────────────────────────────────────

    @BeforeAll
    static void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        wait   = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterAll
    static void stopBrowser() {
        if (driver != null) driver.quit();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Registers a new account via the /register form and verifies the redirect
     * to /login?registered. Does not log in.
     */
    private void register(String username, String email, String password) {
        driver.get(url("/register"));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    /**
     * Logs in via the /login form and verifies the redirect to /calendar.
     */
    private void login(String username, String password) {
        driver.get(url("/login"));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
    }

    /**
     * Logs out via the Sign out button and verifies the redirect to /login?logout.
     */
    private void logout() {
        driver.findElement(By.cssSelector("nav form button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    /**
     * Returns a datetime-local string (yyyy-MM-ddTHH:mm) offset from now by the given hours.
     */
    private String inHours(int hours) {
        return LocalDateTime.now().plusHours(hours).format(DT_LOCAL);
    }

    private void setDateTime(String id, int hours) {
        String val = inHours(hours);
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];", driver.findElement(By.id(id)), val);
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    /**
     * Flow 1 — Registration
     *
     * A new user fills in the registration form. On success they are redirected
     * to /login and a success banner is displayed.
     */
    @Test
    @Order(1)
    void registration_newUser_redirectsToLoginWithSuccessBanner() {
        driver.get(url("/register"));
        driver.findElement(By.id("username")).sendKeys(USER_A);
        driver.findElement(By.id("email")).sendKeys(EMAIL_A);
        driver.findElement(By.id("password")).sendKeys(PASSWORD);
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        wait.until(ExpectedConditions.urlContains("/login?registered"));
        WebElement banner = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success")));

        assertThat(banner.getText()).containsIgnoringCase("Account created");
    }

    /**
     * Flow 2 — Duplicate username rejected
     *
     * Attempting to register with an already-taken username must show an error
     * message on the same /register page (no redirect).
     */
    @Test
    @Order(2)
    void registration_duplicateUsername_showsError() {
        // USER_A was registered in the previous test; try again with the same username.
        driver.get(url("/register"));
        driver.findElement(By.id("username")).sendKeys(USER_A);
        driver.findElement(By.id("email")).sendKeys("other@example.com");
        driver.findElement(By.id("password")).sendKeys(PASSWORD);
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        // Must stay on /register and display an error.
        wait.until(ExpectedConditions.urlContains("/register"));
        WebElement error = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));

        assertThat(error.getText()).isNotBlank();
    }

    /**
     * Flow 3 — Login with wrong password
     *
     * Submitting incorrect credentials must stay on /login and display an error.
     */
    @Test
    @Order(3)
    void login_wrongPassword_showsError() {
        driver.get(url("/login"));
        driver.findElement(By.id("username")).sendKeys(USER_A);
        driver.findElement(By.id("password")).sendKeys("wrongpassword");
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        wait.until(ExpectedConditions.urlContains("/login?error"));
        WebElement error = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));

        assertThat(error.getText()).containsIgnoringCase("Invalid");
    }

    /**
     * Flow 4 — Successful login and unauthenticated redirect
     *
     * A registered user can log in and is redirected to /calendar.
     * Accessing /calendar without being logged in must redirect to /login.
     */
    @Test
    @Order(4)
    void login_validCredentials_landsOnCalendar() {
        login(USER_A, PASSWORD);

        assertThat(driver.getCurrentUrl()).contains("/calendar");
        // The page must display the username in the nav bar.
        assertThat(driver.getPageSource()).contains(USER_A);
    }

    /**
     * Flow 5 — Unauthenticated access is blocked
     *
     * Hitting /calendar after logging out must redirect back to /login.
     */
    @Test
    @Order(5)
    void unauthenticatedAccessToCalendar_redirectsToLogin() {
        // Make sure we're logged in first, then log out.
        login(USER_A, PASSWORD);
        logout();

        driver.get(url("/calendar"));
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl()).contains("/login");
    }

    /**
     * Flow 6 — Propose a meeting (organizer-only, no invitees)
     *
     * After submitting the proposal form the user is redirected to /calendar
     * and the new meeting title appears there.
     */
    @Test
    @Order(6)
    void proposeMeeting_noInvitees_appearsOnCalendar() {
        login(USER_A, PASSWORD);

        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys("Solo Standup");
        setDateTime("start", 1);
        setDateTime("end", 2);
        driver.findElement(By.cssSelector("form[action='/meetings/new'] button[type=submit]")).click();

        wait.until(ExpectedConditions.urlContains("/calendar"));
        assertThat(driver.getPageSource()).contains("Solo Standup");
    }

    /**
     * Flow 7 — Propose a meeting with an invitee
     *
     * USER_A invites USER_B. After submission:
     * - USER_A sees the meeting on their calendar (confirmed = false, tentative badge).
     * - USER_B sees a pending invite section on their calendar.
     */
    @Test
    @Order(7)
    void proposeMeeting_withInvitee_pendingInviteVisibleToInvitee() {
        // Register USER_B if not already registered.
        register(USER_B, EMAIL_B, PASSWORD);

        login(USER_A, PASSWORD);
        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys("Team Sync");
        setDateTime("start", 3);
        setDateTime("end", 4);
        driver.findElement(By.id("invitees")).sendKeys(USER_B);
        driver.findElement(By.cssSelector("form[action='/meetings/new'] button[type=submit]")).click();

        wait.until(ExpectedConditions.urlContains("/calendar"));
        assertThat(driver.getPageSource()).contains("Team Sync");
        logout();

        // USER_B must see the invite in the "Pending invites" section.
        login(USER_B, PASSWORD);
        assertThat(driver.getPageSource()).contains("Team Sync");
        assertThat(driver.getPageSource()).contains("Pending invites");
    }

    /**
     * Flow 8 — Accepting an invite
     *
     * USER_B accepts the "Team Sync" invite. After accepting:
     * - The pending invite section must no longer list "Team Sync".
     * - "Team Sync" must still appear on USER_B's calendar (now accepted).
     */
    @Test
    @Order(8)
    void acceptInvite_removesFromPendingAndKeepsOnCalendar() {
        login(USER_B, PASSWORD);

        // Click the Accept button for "Team Sync".
        WebElement acceptBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//div[contains(@class,'invite') and .//strong[text()='Team Sync']]" +
                         "//form[.//input[@value='accept']]//button")));
        acceptBtn.click();

        wait.until(ExpectedConditions.urlContains("/calendar"));

        String page = driver.getPageSource();
        // "Team Sync" remains on the calendar.
        assertThat(page).contains("Team Sync");
        // The pending invite section must either be gone or not list Team Sync as pending.
        // We verify by checking there is no Accept button still visible for "Team Sync".
        assertThat(driver.findElements(
                By.xpath("//div[contains(@class,'invite') and .//strong[text()='Team Sync']]")))
                .isEmpty();
    }

    /**
     * Flow 9 — Declining an invite
     *
     * USER_B proposes a meeting and invites USER_A. USER_A declines.
     * After declining, the meeting must not appear in USER_A's calendar.
     */
    @Test
    @Order(9)
    void declineInvite_meetingDisappearsFromCalendar() {
        // USER_B proposes and invites USER_A.
        login(USER_B, PASSWORD);
        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys("Board Review");
        setDateTime("start", 5);
        setDateTime("end", 6);
        driver.findElement(By.id("invitees")).sendKeys(USER_A);
        driver.findElement(By.cssSelector("form[action='/meetings/new'] button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
        logout();

        // USER_A declines.
        login(USER_A, PASSWORD);
        WebElement declineBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//div[contains(@class,'invite') and .//strong[text()='Board Review']]" +
                         "//form[.//input[@value='decline']]//button")));
        declineBtn.click();

        wait.until(ExpectedConditions.urlContains("/calendar"));

        // "Board Review" must not be visible anywhere on USER_A's calendar.
        assertThat(driver.findElements(
                By.xpath("//*[contains(text(),'Board Review')]")))
                .isEmpty();
    }

    /**
     * Flow 10 — Proposing a meeting with an end time before start time
     *
     * The form must stay on /meetings/new and display a validation error.
     */
    @Test
    @Order(10)
    void proposeMeeting_endBeforeStart_showsError() {
        login(USER_A, PASSWORD);

        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys("Invalid Meeting");
        // end is before start
        setDateTime("start", 5);
        setDateTime("end", 2);
        driver.findElement(By.cssSelector("form[action='/meetings/new'] button[type=submit]")).click();

        // Must remain on the propose page with an error.
        wait.until(ExpectedConditions.urlContains("/meetings/new"));
        WebElement error = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));

        assertThat(error.getText()).isNotBlank();
    }

    /**
     * Flow 11 — Proposing a meeting with an unknown invitee
     *
     * If the invitee username does not exist, the form must stay on /meetings/new
     * and show an error mentioning the unknown username.
     */
    @Test
    @Order(11)
    void proposeMeeting_unknownInvitee_showsError() {
        login(USER_A, PASSWORD);

        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys("Ghost Meeting");
        setDateTime("start", 1);
        setDateTime("end", 2);
        driver.findElement(By.id("invitees")).sendKeys("nonexistent_user_xyz");
        driver.findElement(By.cssSelector("form[action='/meetings/new'] button[type=submit]")).click();

        wait.until(ExpectedConditions.urlContains("/meetings/new"));
        WebElement error = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));

        assertThat(error.getText()).containsIgnoringCase("nonexistent_user_xyz");
    }

    /**
     * Flow 12 — Logout
     *
     * After clicking Sign out the user is redirected to /login?logout and a
     * logout success banner is displayed.
     */
    @Test
    @Order(12)
    void logout_redirectsToLoginWithSuccessBanner() {
        login(USER_A, PASSWORD);
        logout();

        assertThat(driver.getCurrentUrl()).contains("/login?logout");
        WebElement banner = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".success")));

        assertThat(banner.getText()).containsIgnoringCase("signed out");
    }
}
