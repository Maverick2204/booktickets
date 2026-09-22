# Single-node Redis for demo purposes. Note: this means the Redis distributed lock is only
# as durable as this one node - a production deployment wanting true Redlock guarantees would
# use multiple independent Redis masters, which is out of scope for this demo's cost budget.

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name_prefix}-redis-subnets"
  subnet_ids = var.private_subnet_ids
}

resource "aws_elasticache_cluster" "main" {
  cluster_id           = "${var.name_prefix}-redis"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.node_type
  num_cache_nodes      = 1
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [var.security_group_id]
  parameter_group_name = "default.redis7"
}
