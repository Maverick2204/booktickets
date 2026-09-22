variable "name_prefix" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "db_name" {
  type    = string
  default = "booktickets"
}

variable "db_username" {
  type    = string
  default = "booktickets"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}
