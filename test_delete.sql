DELETE FROM products WHERE id NOT IN (
  SELECT MIN(id) FROM products GROUP BY code
)
