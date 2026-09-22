terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Uncomment and configure for real use - a local backend is fine for review, but any
  # actual apply should use remote state.
  # backend "s3" {
  #   bucket = "booktickets-terraform-state"
  #   key    = "booktickets/terraform.tfstate"
  #   region = "us-east-1"
  # }
}

provider "aws" {
  region = var.aws_region
}
