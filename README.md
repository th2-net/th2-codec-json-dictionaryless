# th2-codec-json-dictionaryless v0.0.1
This microservice can encode and decode JSON messages.

## Configuration
Main configuration is done via setting following properties in codecSettings block of a custom configuration:
- **encodeTypeInfo** - message fields that have content like `boolean(*)` or `number(*)` will be encoded as json booleans and numbers respectively. (default: false)
- **decodeTypeInfo** - numbers and booleans from json tree will be decoded as `boolean(value)` and `number(value)`. (default: false)

## Encoding
Codec will attempt to encode all parsed messages in a message group if their protocol is set to json (or empty)
Messages will be encoded into raw messages containing byte-string with JSON.

## Decoding
Codec will attempt to decode all raw messages in a message group as JSON.

## Deployment via `infra-mgr`
Here's an example of infra-mgr config required to deploy this service

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: ws-json-codec
spec:
  image-name: ghcr.io/th2-net/th2-codec-json-dictionaryless
  image-version: 0.0.1
  custom-config:
    codecSettings:
      encodeTypeInfo: true
      decodeTypeInfo: true
  type: th2-codec
  pins:
    # encoder
    - name: in_codec_encode
      connection-type: mq
      attributes:
        - encoder_in
        - subscribe
    - name: out_codec_encode
      connection-type: mq
      attributes:
        - encoder_out
        - publish
    # decoder
    - name: in_codec_decode
      connection-type: mq
      attributes:
        - decoder_in
        - subscribe
    - name: out_codec_decode
      connection-type: mq
      attributes:
        - decoder_out
        - publish
    # encoder general (technical)
    - name: in_codec_general_encode
      connection-type: mq
      attributes:
        - general_encoder_in
        - subscribe
    - name: out_codec_general_encode
      connection-type: mq
      attributes:
        - general_encoder_out
        - publish
    # decoder general (technical)
    - name: in_codec_general_decode
      connection-type: mq
      attributes:
        - general_decoder_in
        - subscribe
    - name: out_codec_general_decode
      connection-type: mq
      attributes:
        - general_decoder_out
        - publish
  extended-settings:
    service:
      enabled: false
```

