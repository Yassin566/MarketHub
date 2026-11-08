# Core domain model

- User 1..* RefreshToken; User 0..* VendorMembership
- Vendor 1..1 Store; Vendor 1..* Product; Product 1..1 Inventory
- Inventory tracks on-hand and reserved quantities; available quantity is on-hand minus reserved.
- InventoryAdjustment is an append-only audit record linked to the product, vendor, and acting user.
- Customer 1..1 Cart; Cart 1..* CartLine
- Customer 1..* ParentOrder; ParentOrder 1..* VendorOrder; VendorOrder 1..* OrderLine
- ParentOrder 1..* PaymentAttempt; VendorOrder 0..* Refund; Vendor 0..* Payout
- VendorOrder and OrderLine retain immutable financial snapshots
- CommissionRule has scope, effective interval, priority, and rate/flat-fee policy

Public identifiers are UUIDs. Money uses BigDecimal/NUMERIC with explicit currency. Quantities use integer units. Persist timestamps in UTC.
