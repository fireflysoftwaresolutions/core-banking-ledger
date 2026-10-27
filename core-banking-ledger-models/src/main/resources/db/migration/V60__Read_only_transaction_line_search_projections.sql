-- Invoker rights retain the caller's underlying table privileges and forced RLS policies.
-- Joined views are not automatically updatable; only SELECT is granted.

CREATE VIEW public.ledger_search_transaction_line_ach WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_ach line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_ach TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_card WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_card line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_card TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_deposit WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_deposit line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_deposit TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_direct_debit WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_direct_debit line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_direct_debit TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_fee WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_fee line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_fee TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_sepa_transfer WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_sepa_transfer line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_sepa_transfer TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_standing_order WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount,
       (line.standing_order_suspended_until_date > CURRENT_DATE) AS ledger_suspended_on_database_date
FROM public.transaction_line_standing_order line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_standing_order TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_transfer WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_transfer line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_transfer TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_wire_transfer WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_wire_transfer line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_wire_transfer TO firefly_funds_receipt_api;

CREATE VIEW public.ledger_search_transaction_line_withdrawal WITH (security_invoker=true, security_barrier=true) AS
SELECT line.*, txn.total_amount AS ledger_transaction_total_amount
FROM public.transaction_line_withdrawal line INNER JOIN public.transaction txn ON txn.transaction_id=line.transaction_id;
GRANT SELECT ON public.ledger_search_transaction_line_withdrawal TO firefly_funds_receipt_api;
