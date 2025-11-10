# Bank Account Statement Parser
This module parses bank account statements in Excel format for now. The account statement parsing is configuration-file driven and categorize by file types for example for Excel based files the configuration name picked up would be `excelStatmentConfig.json`.

## Usage
    TODO: Include git clone and maven 

## How it works?


- For ICICI bank, one can obtain yearly transaction records by using their Detail Account Statement and providing date range that would be 365 days. Example of date range can be 31-October-2024 to 01-November-2025.
- It is expected that the input to the `BankStatementParserFactor` contains `StatementType` Enum and file to parse or extract data.
- The configuration file is designed to contain various different banks that can be distinguished using the root level JSON object name, for example - `iciciBankSearchStatmentConfig`:
    ```json
    {
      "iciciBankSearchStatementConfig": {
        "version": "1.0",
        "description": "ICICI Bank Account Search Statement"
        //...
      }
    }
    ```