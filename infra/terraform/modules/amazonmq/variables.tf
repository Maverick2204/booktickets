variable "name_prefix" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "mq_username" {
  type    = string
  default = "booktickets"
}

variable "instance_type" {
  type    = string
  default = "mq.t3.micro"
}
