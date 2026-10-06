import {
  Buffer,
} from 'node:buffer';

import {
  env,
} from 'node:process';

import {
  describe,
  expect,
  it,
} from 'vitest';

interface AlfrescoTicketResponse {
  readonly entry: {
    readonly id: string;
    readonly userId: string;
  };
}

interface AlfrescoPersonResponse {
  readonly entry: {
    readonly id: string;
    readonly firstName: string;
    readonly lastName?: string;
    readonly displayName: string;
    readonly email?: string;
    readonly enabled: boolean;

    readonly capabilities: {
      readonly isGuest: boolean;
      readonly isAdmin: boolean;
      readonly isMutable: boolean;
    };
  };
}

function requireEnvironmentVariable(
  name: string,
): string {
  const value =
    env[name]?.trim();

  if (!value) {
    throw new Error(
      `${name} must be set before running integration tests.`,
    );
  }

  return value;
}

function normalizeBaseUrl(
  value: string,
): string {
  return value.replace(
    /\/+$/,
    '',
  );
}

function expectedUserId(
  username: string,
): string {
  const configured =
  env[
    'BADEA_EXPECTED_USER_ID'
  ]?.trim();

  if (configured) {
    return configured.toLowerCase();
  }

  const atIndex =
    username.indexOf('@');

  const accountName =
    atIndex >= 0
      ? username.slice(
          0,
          atIndex,
        )
      : username;

  return accountName
    .trim()
    .toLowerCase();
}

function ticketAuthorizationHeader(
  ticket: string,
): string {
  const encodedTicket =
    Buffer
      .from(
        ticket,
        'utf8',
      )
      .toString('base64');

  return `Basic ${encodedTicket}`;
}

async function assertHttpStatus(
  response: Response,
  expectedStatuses:
    readonly number[],
  operation: string,
): Promise<void> {
  if (
    expectedStatuses.includes(
      response.status,
    )
  ) {
    return;
  }

  throw new Error(
    `${operation} failed: HTTP ${response.status} ${response.statusText}`,
  );
}

describe(
  'Alfresco authentication integration',
  () => {
    it(
      'authenticates through BADEA LDAP and resolves the Alfresco user',
      async () => {
        const baseUrl =
          normalizeBaseUrl(
            requireEnvironmentVariable(
              'ALFRESCO_BASE_URL',
            ),
          );

        const username =
          requireEnvironmentVariable(
            'BADEA_TEST_USERNAME',
          );

        const password =
          requireEnvironmentVariable(
            'BADEA_TEST_PASSWORD',
          );

        const expectedId =
          expectedUserId(
            username,
          );

        let ticket:
          string | null = null;

        try {
          /*
           * 1. Authenticate through Alfresco.
           *
           * Alfresco delegates this user
           * authentication to the configured
           * BADEA LDAP/AD subsystem.
           */
          const loginResponse =
            await fetch(
              `${baseUrl}/api/-default-/public/authentication/versions/1/tickets`,
              {
                method: 'POST',

                headers: {
                  'Content-Type':
                    'application/json',
                },

                body: JSON.stringify({
                  userId: username,
                  password,
                }),
              },
            );

          await assertHttpStatus(
            loginResponse,
            [201],
            'Alfresco login',
          );

          const loginBody =
            await loginResponse
              .json() as
                AlfrescoTicketResponse;

          ticket =
            loginBody.entry.id;

          expect(ticket)
            .toBeTruthy();

          expect(
            loginBody
              .entry
              .userId
              .toLowerCase(),
          ).toBe(expectedId);

          /*
           * 2. Use the returned ticket against
           * the actual Alfresco repository.
           */
          const personResponse =
            await fetch(
              `${baseUrl}/api/-default-/public/alfresco/versions/1/people/-me-`,
              {
                headers: {
                  Authorization:
                    ticketAuthorizationHeader(
                      ticket,
                    ),
                },
              },
            );

          await assertHttpStatus(
            personResponse,
            [200],
            'Load current Alfresco user',
          );

          const personBody =
            await personResponse
              .json() as
                AlfrescoPersonResponse;

          expect(
            personBody
              .entry
              .id
              .toLowerCase(),
          ).toBe(expectedId);

          expect(
            personBody
              .entry
              .displayName
              .trim()
              .length,
          ).toBeGreaterThan(0);

          expect(
            personBody
              .entry
              .enabled,
          ).toBe(true);

          expect(
            personBody
              .entry
              .capabilities
              .isGuest,
          ).toBe(false);

          /*
           * 3. Verify the same ticket can
           * be explicitly invalidated.
           */
          const logoutResponse =
            await fetch(
              `${baseUrl}/api/-default-/public/authentication/versions/1/tickets/-me-`,
              {
                method: 'DELETE',

                headers: {
                  Authorization:
                    ticketAuthorizationHeader(
                      ticket,
                    ),
                },
              },
            );

          await assertHttpStatus(
            logoutResponse,
            [200, 204],
            'Alfresco logout',
          );

          /*
           * Prevent the finally block from
           * trying to revoke it again.
           */
          ticket = null;
        } finally {
          /*
           * Best-effort cleanup.
           *
           * If an assertion fails after the
           * ticket was created, do not leave
           * an unnecessary live test session.
           */
          if (ticket) {
            try {
              await fetch(
                `${baseUrl}/api/-default-/public/authentication/versions/1/tickets/-me-`,
                {
                  method:
                    'DELETE',

                  headers: {
                    Authorization:
                      ticketAuthorizationHeader(
                        ticket,
                      ),
                  },
                },
              );
            } catch {
              /*
               * Do not hide the original test
               * failure with a cleanup error.
               */
            }
          }
        }
      },
    );
  },
);