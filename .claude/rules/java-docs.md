# Java Documentation Standard Rule (MANDATORY)

## Purpose

Every Java source file must have complete, accurate Javadoc that reflects the current implementation. Missing or stale documentation is treated as a bug.

## Requirements

For every class, interface, enum, record, annotation, and non-private method/constructor:

- Add Javadoc if missing; update it if the implementation changed; never leave a public API undocumented.
- Every top-level type includes `@author orvigas@gmail.com` (append after any existing authors — never remove prior attribution).
- Methods document purpose, `@param`, `@return`, `@throws`, and thread-safety/nullability notes when relevant.
- Records document the type's purpose plus one `@param` per component.
- Generic parameters are documented (e.g., `@param <T> entity type`).
- Deprecated APIs include `@deprecated` with the replacement and migration guidance.

## Style

Documentation must be concise and describe intent, not restate the signature.

Bad:
```java
/** Gets name. */
```

Good:
```java
/** Returns the customer's full display name. */
```

## Examples

Class:
```java
/**
 * Handles JWT issuance and validation for authenticated requests.
 *
 * @author orvigas@gmail.com
 */
```

Method:
```java
/**
 * Creates a new customer.
 *
 * @param request customer creation request
 * @return persisted customer
 * @throws DuplicateCustomerException if the customer already exists
 */
```

Record:
```java
/**
 * Represents a customer response.
 *
 * @param id customer identifier
 * @param name customer name
 * @author orvigas@gmail.com
 */
```

Deprecated:
```java
/**
 * @deprecated Use createCustomer(CustomerRequest) instead.
 */
```

## Completion Check

Before finishing a Java task: every new or modified file has current Javadoc, public APIs and constructors are documented, generics have `@param <T>` tags, and every top-level type carries `@author orvigas@gmail.com`.
