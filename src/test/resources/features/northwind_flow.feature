Feature: Northwind Coffee Co. ledger flow

  Background:
    Given a Northwind sub-account exists

  Scenario: USDC deposit credits USD balance after confirmation
    Given Northwind has a zero balance
    When a confirmed USDC deposit of 5000000000 minor units arrives
    Then the available balance increases by 499650 cents
    And itemized fees of 350 cents are posted to the platform fee account

  Scenario: Duplicate deposit is idempotent
    Given a confirmed USDC deposit of 1000000000 minor units with key "idem-bdd-001"
    When the same deposit with key "idem-bdd-001" is submitted again
    Then only one transfer record exists for key "idem-bdd-001"
    And the balance reflects a single credit only

  Scenario: Unconfirmed deposit is not spendable
    When an unconfirmed USDC deposit of 50000000000 minor units arrives
    Then the available USD balance is zero
    And the pending USDC balance is greater than zero

  Scenario: Confirmed deposit triggers route and fires payout job
    When a confirmed USDC deposit of 5000000000 minor units arrives for the Northwind main account
    Then a payout job is created for the outbound route
    And the payout job status is "pending" or "completed"
