package org.badea.correspondence.infrastructure.mail;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explicit, fail-closed recipient restriction for automatic workflow mail ONLY.
 * Manual Share Documents mail is not routed through this policy.
 */
public final class WorkflowEmailDeliveryPolicy {
    private static final String ADDRESS = "[a-z0-9][a-z0-9._-]{0,63}@badea\\.org";
    private final Scope scope;
    private final Set<String> allowed;

    public enum Scope { DISABLED, ALLOWLIST, ALL }

    private WorkflowEmailDeliveryPolicy(Scope scope, Set<String> allowed) {
        this.scope = scope;
        this.allowed = allowed;
    }

    public static WorkflowEmailDeliveryPolicy disabled() {
        return new WorkflowEmailDeliveryPolicy(Scope.DISABLED, Set.of());
    }

    public static WorkflowEmailDeliveryPolicy parse(String mode, String rawAllowed) {
        String value = mode == null ? "" : mode.strip();
        if ("ALLOWLIST".equals(value)) {
            if (rawAllowed == null || rawAllowed.isBlank()) {
                throw new IllegalArgumentException("Workflow email allowlist is required in ALLOWLIST mode");
            }
            String[] entries = rawAllowed.split(",", -1);
            if (entries.length > 20) throw new IllegalArgumentException("Too many workflow email recipients");
            Set<String> allowed = Arrays.stream(entries).map(String::strip)
                .map(address -> address.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
            if (allowed.size() != entries.length || allowed.stream().anyMatch(a -> !a.matches(ADDRESS))) {
                throw new IllegalArgumentException("Invalid or repeated BADEA workflow email recipient");
            }
            return new WorkflowEmailDeliveryPolicy(Scope.ALLOWLIST, allowed);
        }
        if ("ALL".equals(value) && (rawAllowed == null || rawAllowed.isBlank())) {
            return new WorkflowEmailDeliveryPolicy(Scope.ALL, Set.of());
        }
        throw new IllegalArgumentException("Explicit workflow email delivery scope required (ALLOWLIST or ALL)");
    }

    public Scope scope() { return scope; }

    public boolean permits(String email) {
        if (email == null || !email.matches("(?i)" + ADDRESS)) return false;
        return scope == Scope.ALL || (scope == Scope.ALLOWLIST
            && allowed.contains(email.toLowerCase(Locale.ROOT)));
    }
}
