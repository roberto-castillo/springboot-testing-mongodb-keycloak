package com.mycompany.bookservice;

import com.mycompany.bookservice.dto.BookDto;
import com.mycompany.bookservice.dto.CreateBookDto;
import com.mycompany.bookservice.dto.UpdateBookDto;
import com.mycompany.bookservice.model.Book;
import com.mycompany.bookservice.repository.BookRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mycompany.bookservice.helper.BookServiceTestHelper.getDefaultBook;
import static com.mycompany.bookservice.helper.BookServiceTestHelper.getDefaultCreateBookDto;
import static com.mycompany.bookservice.helper.BookServiceTestHelper.getDefaultUpdateBookDto;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/bookdb" // connecting to embedded mongodb provided by 'de.flapdoodle.embed:de.flapdoodle.embed.mongo'
})
public class RandomPortTestRestTemplateTests {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private TestRestTemplate testRestTemplate;

    private static Keycloak keycloakAdmin;
    private static Keycloak keycloakBookService;

    @BeforeAll
    static void setUp() {
        keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK_SERVER_URL)
                .realm("master")
                .username("admin")
                .password("admin")
                .clientId("admin-cli")
                .build();

        // Realm
        RealmRepresentation realmRepresentation = new RealmRepresentation();
        realmRepresentation.setRealm(COMPANY_SERVICE_REALM_NAME);
        realmRepresentation.setEnabled(true);

        // Client
        ClientRepresentation clientRepresentation = new ClientRepresentation();
        clientRepresentation.setId(BOOK_SERVICE_CLIENT_ID);
        clientRepresentation.setDirectAccessGrantsEnabled(true);
        clientRepresentation.setSecret(BOOK_SERVICE_CLIENT_SECRET);
        realmRepresentation.setClients(Collections.singletonList(clientRepresentation));

        // Client roles
        Map<String, List<String>> clientRoles = new HashMap<>();
        clientRoles.put(BOOK_SERVICE_CLIENT_ID, BOOK_SERVICE_ROLES);

        // Credentials
        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setValue(USER_PASSWORD);

        // User
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(USER_USERNAME);
        userRepresentation.setEnabled(true);
        userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));
        userRepresentation.setClientRoles(clientRoles);
        realmRepresentation.setUsers(Collections.singletonList(userRepresentation));

        keycloakAdmin.realms().create(realmRepresentation);

        keycloakBookService = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK_SERVER_URL)
                .realm(COMPANY_SERVICE_REALM_NAME)
                .username(USER_USERNAME)
                .password(USER_PASSWORD)
                .clientId(BOOK_SERVICE_CLIENT_ID)
                .clientSecret(BOOK_SERVICE_CLIENT_SECRET)
                .build();
    }

    @AfterAll
    static void tearDown() {
        keycloakAdmin.realm(COMPANY_SERVICE_REALM_NAME).remove();
    }

    @Test
    void givenNoBooksWhenGetAllBooksThenReturnStatusOkAndEmptyArray() {
        ResponseEntity<BookDto[]> responseEntity = testRestTemplate.getForEntity(API_BOOKS_URL, BookDto[].class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).hasSize(0);
    }

    @Test
    void givenOneBookWhenGetAllBooksThenReturnStatusOkAndArrayWithOneBook() {
        Book book = getDefaultBook();
        bookRepository.save(book);

        ResponseEntity<BookDto[]> responseEntity = testRestTemplate.getForEntity(API_BOOKS_URL, BookDto[].class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).hasSize(1);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody()[0].getId()).isEqualTo(book.getId());
        assertThat(responseEntity.getBody()[0].getAuthorName()).isEqualTo(book.getAuthorName());
        assertThat(responseEntity.getBody()[0].getTitle()).isEqualTo(book.getTitle());
        assertThat(responseEntity.getBody()[0].getPrice()).isEqualTo(book.getPrice());
    }

    @Test
    void givenValidBookWhenCreateBookWithoutAuthenticationThenReturnStatus302() {
        CreateBookDto createBookDto = getDefaultCreateBookDto();
        ResponseEntity<String> responseEntity = testRestTemplate.postForEntity(API_BOOKS_URL, createBookDto, String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(responseEntity.getBody()).isNull();
    }

    @Test
    void givenValidBookWhenCreateBookInformingInvalidTokenThenReturnStatusUnauthorized() {
        CreateBookDto createBookDto = getDefaultCreateBookDto();

        HttpHeaders headers = authBearerHeaders("abcdef");
        ResponseEntity<String> responseEntity = testRestTemplate.postForEntity(API_BOOKS_URL, new HttpEntity<>(createBookDto, headers), String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getBody()).isNull();
    }

    @Test
    void givenValidBookWhenCreateBookAuthenticatedThenReturnStatusCreatedAndBookJson() {
        CreateBookDto createBookDto = getDefaultCreateBookDto();

        String accessToken = keycloakBookService.tokenManager().grantToken().getToken();

        HttpHeaders headers = authBearerHeaders(accessToken);
        ResponseEntity<BookDto> responseEntity = testRestTemplate.postForEntity(API_BOOKS_URL, new HttpEntity<>(createBookDto, headers), BookDto.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getId()).isNotNull();
        assertThat(responseEntity.getBody().getAuthorName()).isEqualTo(createBookDto.getAuthorName());
        assertThat(responseEntity.getBody().getTitle()).isEqualTo(createBookDto.getTitle());
        assertThat(responseEntity.getBody().getPrice()).isEqualTo(createBookDto.getPrice());
    }

    @Test
    void givenNonExistingBookIdWhenUpdateBookThenReturnStatusNotFound() {
        UUID id = UUID.randomUUID();
        UpdateBookDto updateBookDto = getDefaultUpdateBookDto();

        String accessToken = keycloakBookService.tokenManager().grantToken().getToken();
        HttpHeaders headers = authBearerHeaders(accessToken);

        String url = String.format(API_BOOKS_ID_URL, id);
        ResponseEntity<MessageError> responseEntity = testRestTemplate.exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(updateBookDto, headers), MessageError.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getTimestamp()).isNotEmpty();
        assertThat(responseEntity.getBody().getStatus()).isEqualTo(404);
        assertThat(responseEntity.getBody().getError()).isEqualTo(ERROR_NOT_FOUND);
        assertThat(responseEntity.getBody().getMessage()).isEqualTo("Book with id '" + id + "' not found.");
        assertThat(responseEntity.getBody().getPath()).isEqualTo(url);
        assertThat(responseEntity.getBody().getErrors()).isNull();
    }

    @Test
    void givenExistingBookWhenUpdateBookThenReturnStatusOkAndBookJson() {
        Book book = getDefaultBook();
        bookRepository.save(book);

        UpdateBookDto updateBookDto = new UpdateBookDto();
        updateBookDto.setAuthorName("Ivan Franchin Jr.");
        updateBookDto.setTitle("Java 9");

        String accessToken = keycloakBookService.tokenManager().grantToken().getToken();
        HttpHeaders headers = authBearerHeaders(accessToken);

        String url = String.format(API_BOOKS_ID_URL, book.getId());
        ResponseEntity<BookDto> responseEntity = testRestTemplate.exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(updateBookDto, headers), BookDto.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getId()).isNotNull();
        assertThat(responseEntity.getBody().getAuthorName()).isEqualTo(updateBookDto.getAuthorName());
        assertThat(responseEntity.getBody().getTitle()).isEqualTo(updateBookDto.getTitle());
        assertThat(responseEntity.getBody().getPrice()).isEqualTo(book.getPrice());
    }

    @Test
    void givenNonExistingBookIdWhenDeleteBookThenReturnStatusNotFound() {
        UUID id = UUID.randomUUID();
        String accessToken = keycloakBookService.tokenManager().grantToken().getToken();

        HttpHeaders headers = authBearerHeaders(accessToken);

        String url = String.format(API_BOOKS_ID_URL, id);
        ResponseEntity<MessageError> responseEntity = testRestTemplate.exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(headers), MessageError.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getTimestamp()).isNotEmpty();
        assertThat(responseEntity.getBody().getStatus()).isEqualTo(404);
        assertThat(responseEntity.getBody().getError()).isEqualTo(ERROR_NOT_FOUND);
        assertThat(responseEntity.getBody().getMessage()).isEqualTo("Book with id '" + id + "' not found.");
        assertThat(responseEntity.getBody().getPath()).isEqualTo(url);
        assertThat(responseEntity.getBody().getErrors()).isNull();
    }

    @Test
    void givenExistingBookIdWhenDeleteBookThenReturnStatusOkAndBookJson() {
        Book book = getDefaultBook();
        bookRepository.save(book);

        String accessToken = keycloakBookService.tokenManager().grantToken().getToken();
        HttpHeaders headers = authBearerHeaders(accessToken);

        String url = String.format(API_BOOKS_ID_URL, book.getId());
        ResponseEntity<BookDto> responseEntity = testRestTemplate.exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(headers), BookDto.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getId()).isNotNull();
        assertThat(responseEntity.getBody().getAuthorName()).isEqualTo(book.getAuthorName());
        assertThat(responseEntity.getBody().getTitle()).isEqualTo(book.getTitle());
        assertThat(responseEntity.getBody().getPrice()).isEqualTo(book.getPrice());
    }

    private HttpHeaders authBearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        return headers;
    }

    private static final String KEYCLOAK_SERVER_URL = "http://localhost:8080/auth";
    private static final String COMPANY_SERVICE_REALM_NAME = "company-services";
    private static final String BOOK_SERVICE_CLIENT_ID = "book-service";
    private static final String BOOK_SERVICE_CLIENT_SECRET = "abc123";
    private static final List<String> BOOK_SERVICE_ROLES = Collections.singletonList("manage_books");
    private static final String USER_USERNAME = "ivan.franchin";
    private static final String USER_PASSWORD = "123";

    private static final String API_BOOKS_URL = "/api/books";
    private static final String API_BOOKS_ID_URL = "/api/books/%s";

    private static final String ERROR_NOT_FOUND = "Not Found";

}
