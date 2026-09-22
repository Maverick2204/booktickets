output "amqp_endpoint" {
  value = aws_mq_broker.main.instances[0].endpoints[0]
}

output "credentials_secret_arn" {
  value = aws_secretsmanager_secret.mq_credentials.arn
}
