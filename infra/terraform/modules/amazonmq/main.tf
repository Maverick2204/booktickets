resource "random_password" "mq_password" {
  length  = 24
  special = false
}

resource "aws_secretsmanager_secret" "mq_credentials" {
  name = "${var.name_prefix}-mq-credentials"
}

resource "aws_secretsmanager_secret_version" "mq_credentials" {
  secret_id = aws_secretsmanager_secret.mq_credentials.id
  secret_string = jsonencode({
    username = var.mq_username
    password = random_password.mq_password.result
  })
}

resource "aws_mq_broker" "main" {
  broker_name        = "${var.name_prefix}-rabbitmq"
  engine_type        = "RabbitMQ"
  engine_version     = "3.13"
  host_instance_type = var.instance_type
  deployment_mode    = "SINGLE_INSTANCE"

  subnet_ids          = [var.subnet_id]
  security_groups     = [var.security_group_id]
  publicly_accessible = false

  user {
    username = var.mq_username
    password = random_password.mq_password.result
  }
}
