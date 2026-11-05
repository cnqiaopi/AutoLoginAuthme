package Mistral.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialCommandsTest {
    @Test void handlesPlayerPasswordAliasesAndNamespace() {
        for (String label : new String[]{"changepassword", "changepass", "cp", "authme:cp"})
            assertEquals("Alice", CredentialCommands.affectedPlayer("/" + label + " old new", "Alice", false));
    }
    @Test void handlesAuthorizedAdminPasswordChange() { assertEquals("Bob", CredentialCommands.affectedPlayer("authme password Bob secret", null, true)); }
    @Test void rejectsUnauthorizedAdminTargetAndMalformedNames() {
        assertNull(CredentialCommands.affectedPlayer("authme password Bob secret", "Alice", false));
        assertNull(CredentialCommands.affectedPlayer("authme password ../Bob secret", null, true));
    }
    @Test void handlesEmailPasswordRecovery() { assertEquals("Alice", CredentialCommands.affectedPlayer("/email setpassword new", "Alice", false)); }
    @Test void ignoresNormalLoginAndIncompleteCommands() {
        assertNull(CredentialCommands.affectedPlayer("/login password", "Alice", false));
        assertNull(CredentialCommands.affectedPlayer("/cp", "Alice", false));
        assertNull(CredentialCommands.affectedPlayer("/authme password Bob", null, true));
    }
}
