# th2-codec-json-dictionaryless v0.5.1
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
apiVersion: th2.exactpro.com/v2
kind: Th2Box
metadata:
  name: ws-json-codec
spec:
  imageName: ghcr.io/th2-net/th2-codec-json-dictionaryless
  imageVersion: 0.1.0
  type: th2-codec
  customConfig:
    transportLines:
      general:
        type: TH2_TRANSPORT
        useParentEventId: true
      lw:
        type: TH2_TRANSPORT
        useParentEventId: false
      rpt:
        type: TH2_TRANSPORT
        useParentEventId: false
    codecSettings:
      encodeTypeInfo: true
      decodeTypeInfo: true
  pins:
    mq:
      subscribers:
        - name: in_codec_general_decode
          attributes: [general_decoder_in, transport-group, subscribe]
        - name: in_codec_general_encode
          attributes: [general_encoder_in, transport-group, subscribe]

        - name: in_codec_lw_decode
          attributes: [lw_decoder_in, transport-group, subscribe]
        - name: in_codec_lw_encode
          attributes: [lw_encoder_in, transport-group, subscribe]

        - name: in_codec_rpt_decode
          attributes: [rpt_decoder_in, transport-group, subscribe]
        - name: in_codec_rpt_encode
          attributes: [rpt_encoder_in, transport-group, subscribe]
      publishers:
        - name: out_codec_general_decode
          attributes: [general_decoder_out, transport-group, publish]
        - name: out_codec_general_encode
          attributes: [general_encoder_out, transport-group, publish]

        - name: out_codec_lw_decode
          attributes: [lw_decoder_out, transport-group, publish]
        - name: out_codec_lw_encode
          attributes: [lw_encoder_out, transport-group, publish]

        - name: out_codec_rpt_decode
          attributes: [rpt_decoder_out, transport-group, publish]
        - name: out_codec_rpt_encode
          attributes: [rpt_encoder_out, transport-group, publish]
  extended-settings:
    service:
      enabled: false
```

## Changelog

### 0.5.1
+ Updated:
  + th2-common: `2.15.0-dev`
  + kotlin-logging: `7.0.6`
+ Updated gradle plugins:
  + th2 gradle plugin: `0.2.4` (bom: `4.11.0`)
  + kotlin: `2.1.20`

### 0.5.0

+ Update th2 gradle plugin: `0.1.3`
+ Update common: `5.14.0-dev`
+ Update kotlin-logging: `5.1.4`

### 0.4.0
+ Migrated to th2 gradle plugin: `0.0.6`
+ Updated:
    + bom `4.6.1`
    + common: `5.10.1-dev`
    + codec: `5.5.0-dev`

### 0.3.0
+ th2 transport protocol support.
+ Updated common: `5.7.2-dev`
+ Updated codec: `5.4.1-dev`
+ Updated kotlin: `1.8.22`

### 0.2.0
+ Updated common, bom and codec to remove vulnerabilities.
+ Added vulnerability check pipeline step

### 0.1.0
+ Updated kotlin to 1.6.21
+ Updated BOM, common and codec to remove vulnerable dependencies