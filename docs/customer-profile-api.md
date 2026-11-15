# Customer profile API and account editor

Customers can maintain their own first name, last name, and optional phone number from the **Personal profile** section of the account dashboard.

## API

Both endpoints require an authenticated account with the `CUSTOMER` role. The server derives the customer ID from the authenticated principal; clients cannot choose another account.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/v1/customer/profile` | Returns the current customer's profile. A customer without a saved profile receives empty name and phone values. |
| PUT | `/api/v1/customer/profile` | Creates or updates the current customer's profile. |

Request body:

```json
{
  "firstName": "Ada",
  "lastName": "Lovelace",
  "phone": "+216 20 123 456"
}
```

- `firstName` and `lastName` are required, trimmed, and limited to 80 characters each.
- `phone` is optional and limited to 32 characters. Blank values are stored as null.
- Repeated updates replace the profile values for the authenticated customer.
- Profile records are deleted automatically when their owning user is deleted.

## Storage

Migration `V20__customer_profiles.sql` creates one profile row per customer, keyed by the user ID with a cascading foreign key to `app_users`. Existing accounts remain valid and receive an empty profile response until they save their details.

## Frontend

The customer account dashboard loads profile details alongside recent orders and notifications. The editor uses browser autocomplete hints, required name fields, inline saving feedback, and a responsive layout. The customer service calls only the customer-scoped API paths and does not submit a user ID.

## Validation

- Backend integration coverage verifies initial empty profiles, trimmed create/update behavior, persistence, validation, and customer-role restrictions.
- Angular service tests verify GET and PUT contracts.
- Playwright coverage verifies editing and saving the profile from the account dashboard.
