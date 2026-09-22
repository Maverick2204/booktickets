locals {
  container_image = coalesce(var.container_image, "${module.ecr.repository_url}:latest")
}

module "network" {
  source      = "./modules/network"
  name_prefix = var.name_prefix
}

module "ecr" {
  source          = "./modules/ecr"
  repository_name = var.name_prefix
}

module "rds" {
  source             = "./modules/rds"
  name_prefix        = var.name_prefix
  private_subnet_ids = module.network.private_subnet_ids
  security_group_id  = module.network.rds_security_group_id
}

module "elasticache" {
  source             = "./modules/elasticache"
  name_prefix        = var.name_prefix
  private_subnet_ids = module.network.private_subnet_ids
  security_group_id  = module.network.redis_security_group_id
}

module "amazonmq" {
  source            = "./modules/amazonmq"
  name_prefix       = var.name_prefix
  subnet_id         = module.network.private_subnet_ids[0]
  security_group_id = module.network.mq_security_group_id
}

module "alb" {
  source            = "./modules/alb"
  name_prefix       = var.name_prefix
  vpc_id            = module.network.vpc_id
  public_subnet_ids = module.network.public_subnet_ids
  security_group_id = module.network.alb_security_group_id
}

module "ecs" {
  source = "./modules/ecs"

  name_prefix     = var.name_prefix
  aws_region      = var.aws_region
  container_image = local.container_image

  private_subnet_ids    = module.network.private_subnet_ids
  ecs_security_group_id = module.network.ecs_security_group_id
  target_group_arn      = module.alb.target_group_arn

  secret_arns = [
    module.rds.credentials_secret_arn,
    module.amazonmq.credentials_secret_arn,
  ]

  db_endpoint               = module.rds.endpoint
  db_port                   = module.rds.port
  db_name                   = "booktickets"
  db_credentials_secret_arn = module.rds.credentials_secret_arn

  redis_endpoint = module.elasticache.endpoint
  redis_port     = module.elasticache.port

  mq_host                   = module.amazonmq.amqp_endpoint
  mq_credentials_secret_arn = module.amazonmq.credentials_secret_arn
}
