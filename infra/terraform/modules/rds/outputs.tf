output "endpoint" {
  value = aws_db_instance.main.address
}

output "port" {
  value = aws_db_instance.main.port
}

output "credentials_secret_arn" {
  value = aws_secretsmanager_secret.db_credentials.arn
}
