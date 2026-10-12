package com.naukriradar.core.controller;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-up, sign-in and the admin's user monitoring, against MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

	private static final String USER_HEADER = "X-User-Id";

	@Autowired
	private MockMvc mvc;

	@Test
	void signUpSignInAndTheGatewayCanResolveTheSession() throws Exception {
		String email = "auth-" + UUID.randomUUID() + "@example.com";
		String token = JsonPath.read(register("Asha Rao", email, "correct horse 1")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.name").value("Asha Rao"))
				.andReturn().getResponse().getContentAsString(), "$.token");
		String userId = resolve(token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"))
				.andReturn().getResponse().getContentAsString().replaceAll(".*\"userId\":\"([^\"]+)\".*", "$1");

		register("Again", email, "another pass 2").andExpect(status().isConflict());
		login(email.toUpperCase(), "correct horse 1").andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
		mvc.perform(get("/api/v1/auth/me").header(USER_HEADER, userId))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.mustChangePassword").value(false));

		mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
		resolve(token).andExpect(status().isNotFound());
		resolve("not-a-token").andExpect(status().isNotFound());
	}

	@Test
	void fiveWrongPasswordsLockTheAccountAndAShortPasswordIsRefused() throws Exception {
		String email = "lock-" + UUID.randomUUID() + "@example.com";
		register("Lock Test", email, "short").andExpect(status().isBadRequest());
		register("Lock Test", email, "right password 1").andExpect(status().isCreated());
		for (int i = 0; i < 5; i++) {
			login(email, "wrong password").andExpect(status().isUnauthorized());
		}
		login(email, "right password 1").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Too many wrong passwords. Try again in 15 minutes."));
	}

	@Test
	void anAccountFromBeforeSignInIsClaimedBySigningUpAndKeepsItsId() throws Exception {
		String email = "old-" + UUID.randomUUID() + "@example.com";
		String oldId = JsonPath.read(mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + email + "\"}")).andReturn().getResponse().getContentAsString(), "$.id");

		login(email, "anything 123").andExpect(status().isUnauthorized());
		register("Old User", email, "now with a password").andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(oldId));
	}

	@Test
	void anAdminSeesUsersDisablesThemAndHandsOutATemporaryPassword() throws Exception {
		String admin = JsonPath.read(register("Admin", "admin-" + UUID.randomUUID() + "@example.com", "admin password 1")
				.andReturn().getResponse().getContentAsString(), "$.userId");
		String email = "watched-" + UUID.randomUUID() + "@example.com";
		String user = JsonPath.read(register("Watched", email, "user password 1").andReturn().getResponse().getContentAsString(),
				"$.userId");

		mvc.perform(get("/api/v1/admin/users").header(USER_HEADER, admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].email", hasItem(email)));
		mvc.perform(get("/api/v1/admin/users/" + user).header(USER_HEADER, admin))
				.andExpect(jsonPath("$.user.name").value("Watched"))
				.andExpect(jsonPath("$.applicationStats.total").value(0));

		// disabled: can't sign in, and sessions stop resolving
		String token = JsonPath.read(login(email, "user password 1").andReturn().getResponse().getContentAsString(), "$.token");
		mvc.perform(patch("/api/v1/admin/users/" + user + "/status").header(USER_HEADER, admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DISABLED\"}"))
				.andExpect(jsonPath("$.status").value("DISABLED"));
		resolve(token).andExpect(status().isNotFound());
		login(email, "user password 1").andExpect(status().isUnauthorized());

		mvc.perform(patch("/api/v1/admin/users/" + user + "/status").header(USER_HEADER, admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"));
		String temporary = JsonPath.read(mvc.perform(post("/api/v1/admin/users/" + user + "/reset-password").header(USER_HEADER, admin))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.temporaryPassword");
		login(email, "user password 1").andExpect(status().isUnauthorized());
		login(email, temporary).andExpect(jsonPath("$.mustChangePassword").value(true));
		mvc.perform(post("/api/v1/auth/change-password").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\": \"" + temporary + "\", \"newPassword\": \"my own password 9\"}"))
				.andExpect(status().isNoContent());
		login(email, "my own password 9").andExpect(jsonPath("$.mustChangePassword").value(false));

		// an admin can't lock themselves out
		mvc.perform(patch("/api/v1/admin/users/" + admin + "/status").header(USER_HEADER, admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DISABLED\"}"))
				.andExpect(status().isBadRequest());
	}

	private ResultActions register(String name, String email, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
	}

	private ResultActions resolve(String token) throws Exception {
		return mvc.perform(post("/internal/v1/sessions/resolve").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\"}"));
	}

}
