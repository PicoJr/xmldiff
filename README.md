# XMLDIFF

xmldiff powered by https://github.com/xmlunit/xmlunit

**very WIP**

example:

```
./gradlew run --args="test-data/simple_left.xml test-data/simple_right.xml --ignore /document/ignore"
```

output:

```json
{
  "same": false,
  "different": [
    "found: (reference test) expected: (reference) at: /document[1]/different[1]/text()[1]",
    "found: (1.1) expected: (1.0) at: /document[1]/numeric[1]/text()[1]",
    "found: (8.0) expected: (4.0) at: /document[1]/numeric_bad[1]/text()[1]",
    "found: (1) expected: (1.01) at: /document[1]/more_numeric[1]/value[1]/text()[1]",
    "found: (2) expected: (2.5) at: /document[1]/more_numeric[1]/value[2]/text()[1]"
  ],
  "ignored": [
    "found: (14/07/2020) expected: (25/12/2020) at: /document[1]/ignore[1]/text()[1]"
  ]
}
```
