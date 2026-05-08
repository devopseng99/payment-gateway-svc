{{/*
Expand the name of the chart.
*/}}
{{- define "payment-gateway-svc.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "payment-gateway-svc.fullname" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "payment-gateway-svc.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "payment-gateway-svc.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "payment-gateway-svc.selectorLabels" -}}
app.kubernetes.io/name: {{ include "payment-gateway-svc.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
