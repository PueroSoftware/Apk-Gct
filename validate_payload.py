import json
import sys
from jsonschema import validate, ValidationError

SCHEMA_FILE = 'payload-schema.json'
EXAMPLE_FILE = 'payload_example.json'


def load(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def main():
    try:
        schema = load(SCHEMA_FILE)
        payload = load(EXAMPLE_FILE)
    except Exception as e:
        print('Error leyendo archivos:', e)
        sys.exit(2)

    try:
        validate(instance=payload, schema=schema)
        print('VALID: payload cumple el schema')
    except ValidationError as ve:
        print('INVALID: payload NO cumple el schema')
        print(ve)
        sys.exit(1)


if __name__ == '__main__':
    main()

