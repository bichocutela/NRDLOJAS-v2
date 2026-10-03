CREATE SCHEMA IF NOT EXISTS nrd_gateway_private;
REVOKE ALL ON SCHEMA nrd_gateway_private FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA nrd_gateway_private TO service_role;
CREATE TABLE nrd_gateway_private.price_rate_limits (
 subject text PRIMARY KEY CHECK (length(subject) BETWEEN 1 AND 128),
 bucket_start timestamptz NOT NULL,
 requests integer NOT NULL CHECK(requests >= 1)
);
ALTER TABLE nrd_gateway_private.price_rate_limits ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON nrd_gateway_private.price_rate_limits FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON nrd_gateway_private.price_rate_limits TO service_role;
CREATE FUNCTION public.consume_nrd_price_budget(p_subject text) RETURNS boolean
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE current_bucket timestamptz := date_trunc('minute', now()); counter integer;
BEGIN
 IF p_subject IS NULL OR length(p_subject) NOT BETWEEN 1 AND 128 THEN RETURN false; END IF;
 DELETE FROM nrd_gateway_private.price_rate_limits WHERE bucket_start < current_bucket - interval '10 minutes';
 INSERT INTO nrd_gateway_private.price_rate_limits AS limits(subject,bucket_start,requests)
 VALUES(p_subject,current_bucket,1)
 ON CONFLICT(subject) DO UPDATE SET
 requests = CASE WHEN limits.bucket_start = current_bucket THEN LEAST(limits.requests+1,61) ELSE 1 END,
 bucket_start = current_bucket
 RETURNING requests INTO counter;
 RETURN counter <= 60;
END; $$;
REVOKE ALL ON FUNCTION public.consume_nrd_price_budget(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.consume_nrd_price_budget(text) TO service_role;