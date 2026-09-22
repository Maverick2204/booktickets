variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "name_prefix" {
  type    = string
  default = "booktickets"
}

variable "container_image" {
  description = "Full ECR image URI:tag to deploy. Defaults to :latest on the module's own ECR repo."
  type        = string
  default     = null
}
