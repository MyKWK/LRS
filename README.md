# LRS-DBTest

LRS-DBTest is a database testing tool designed to enhance database stability and error detection capabilities using our custom-developed LRS testing method. The tool automatically generates SQL queries and interacts with the database to help developers uncover potential issues.

## Project Overview

LRS-DBTest is an automated database testing tool that uses our custom LRS testing method. This testing method generates more diverse SQL queries, increasing test coverage and efficiency. By leveraging LLMs to address shortcomings of existing tools, the tool supports testing for multiple databases including MySQL, TiDB, MariaDB, and OceanBase, aiming to help developers identify potential issues in SQL query execution.

## Features

- **LRS Testing Method**: Utilizes our custom LRS testing method to generate SQL queries, improving error detection accuracy.
- **Highly Configurable**: Users can customize test data scale, testing rules, and other configurations as needed.

## Installation

### Dependencies

- Java 8 or higher
- Maven 3.0 or higher

### Installation Steps

1. Clone this project:
   ```bash
   git clone https://github.com/MyKWK/LRS.git
## Usage
Before using the tool, you can modify the config file to configure the relevant parameters for the LRS testing method. Configurations include test data scale, testing rules, and more.
### Database Setup
Create the corresponding tpcd database in your database instance.
1. MySQL
   ```bash
   mysql -u root -p tpcd < tpcd.sql
2. TiDB
   ```bash  
   mysql -h 127.0.0.1 -P 4000 -u root -p tpcd < tpcd.sql
3. MariaDB
   ```bash  
   mariadb -u root -p tpcd < tpcd.sql
4. OceanBase
   ```bash
   obclient -h127.0.0.1 -P2881 -uroot -p'PASSWORD' -Doceanbase -A
   source tpcd.sql
### Running the Tests
Execute the following command to start the tests:
   ```bash
   mvn exec:java -Dexec.args="--num-threads your_set --username your_name --password your_password --port 3306 mysql --oracle LRS"
