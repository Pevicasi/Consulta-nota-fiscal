# Resultados verificados

## 1.0.1 — issue #2

Código aprovado: [`d068e38d165d27ea5cd253d3c495c46c10d3722e`](https://github.com/Pevicasi/Consulta-nota-fiscal/commit/d068e38d165d27ea5cd253d3c495c46c10d3722e). [GitHub Actions concluído com sucesso](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40376397656). O commit posterior que registra este resultado altera somente documentação.

[**Baixar APK aprovado 1.0.1**](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40376397656/artifacts/12674272860) — extraia o ZIP, confira `SHA256SUMS` e instale `app-debug.apk`. O pacote é `br.com.pevicasi.nfefacil`, versão `1.0.1`, código `2`, Android mínimo 8.0/API 26. A assinatura é de desenvolvimento; permanecem válidas as orientações sobre atualização entre assinaturas diferentes em `android/README.md`.

| Verificação | Resultado observado |
|---|---|
| Regressão web | 8 testes passaram; 0 falhas; 0 ignorados |
| Validação unitária da chave | 5 testes passaram; 0 falhas; 0 ignorados |
| Integração Android | 12 testes passaram no emulador Android 15/API 35 x86_64; 0 falhas; 0 ignorados |
| Scanner embarcado | CODE 128 e OCR leram imagens sintéticas com rede desligada; caixas de código retornadas pelo detector conferidas |
| Moldura | Testada renderização na região detectada, limpeza do estado e transformação de coordenadas com recorte, quatro rotações e espelhamento |
| Confirmação | Botão explícito retorna a chave; duplicação bloqueada; nova leitura e restauração dos 44 dígitos após recriação da Activity verificadas |
| Lint do Actions | 0 erros, 37 avisos; nenhuma assertion desativada ou erro suprimido |
| Build | `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passaram localmente e no Actions; `connectedDebugAndroidTest` passou no Actions |
| APK baixado do Actions | SHA-256 conferido contra `SHA256SUMS`, assinatura APK v2 validada novamente com `apksigner` e versão conferida |
| Site preservado | Os quatro HTMLs permanecem sem alteração e são idênticos aos assets do APK baixado |
| Limpeza | Excluídos três artefatos APK anteriores, incluindo o candidato reprovado; mantido somente o APK aprovado deste workflow. Relatórios e commits preservados |

SHA-256 do **APK baixado do Actions**: `25239dacce4976abd6ce5d82a7d4b8d88cdb27ae80a08ec028902cc04cbdc6b8`.

Relatórios `validation-reports` e `device-test-reports` foram baixados e conferidos. A primeira execução da issue #2 identificou falha real na restauração do diálogo: `onPostResume` antecedia o estado `RESUMED` do Lifecycle. A restauração foi transferida ao observador desse evento, e o mesmo teste passou na execução aprovada, junto com os demais testes.

Não houve teste em aparelho físico. Alinhamento e suavidade sobre câmera real, diferentes fabricantes, iluminação, foco e flash ainda exigem conferência em hardware. A geometria foi validada por transformações sintéticas e a recriação da Activity por teste instrumentado. Consultas governamentais reais, captcha, impressão física e geolocalização real não foram exercitados.

## 1.0.0 — issue #1 (histórico)

Código compilado e testado na `main`: [`ad026e37a3fc1a2a0c1e9b078a36c2072a973a39`](https://github.com/Pevicasi/Consulta-nota-fiscal/commit/ad026e37a3fc1a2a0c1e9b078a36c2072a973a39).

[Execução GitHub Actions concluída com sucesso](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40360759163). Os relatórios e logs foram baixados e conferidos, além do status geral do workflow.

| Verificação | Resultado observado |
|---|---|
| Regressão do site/ponte Android (`npm test`) | 8 testes passaram; 0 falhas; 0 ignorados |
| Validação de chaves (`testDebugUnitTest`) | 5 testes passaram; 0 falhas; 0 ignorados |
| Integração Android (`connectedDebugAndroidTest`) | 7 testes passaram no emulador Android 15/API 35 x86_64; 0 falhas; 0 ignorados |
| Modelos embarcados | ML Kit leu CODE 128 e chave impressa de 44 dígitos em imagens sintéticas com Wi-Fi e dados móveis do emulador desligados |
| Integração local | Tela WebView carregada; chave recebida e persistida; XML/custo ST conferidos; retorno de Preços para Notas; limites do recorte; abertura/fechamento do scanner |
| Lint (`lintDebug`) | 0 erros, 35 avisos; nenhum erro suprimido ou assertion desativada |
| APK e APK de testes | `assembleDebug` e `assembleDebugAndroidTest` concluídos; app do mesmo commit instalado e exercitado pelo teste instrumentado |
| Artefato para download | ZIP baixado do Actions; SHA-256 conferido contra `SHA256SUMS`; assinatura APK v2 validada novamente com `apksigner` |
| Preservação do site | Os quatro HTMLs no APK baixado são idênticos aos arquivos do repositório. No site, somente quatro registros de eventos opcionais foram alterados |
| Reprodutibilidade local | Gradle Wrapper 8.13 com checksum; build limpo, testes, lint e APKs executados com sucesso no ambiente de desenvolvimento |

Os avisos de lint incluem versões de dependências/SDK, suporte necessário a JavaScript no WebView, acessibilidade do gesto da câmera e textos fixos em português. Eles estão disponíveis no relatório completo; não foram ocultados para tornar o build verde.

### Obter e instalar — versão histórica

Os artefatos APK desta versão foram removidos após a aprovação da 1.0.1. Os links e checksums abaixo ficam como registro histórico; use o download atual no início deste documento. Relatórios e histórico Git foram preservados.

[Baixar o artefato do APK](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40360759163/artifacts/12667849827) — `nfe-facil-debug-ad026e37a3fc1a2a0c1e9b078a36c2072a973a39`.

O GitHub pode exigir login para baixar artefatos. Extraia o ZIP, confira o checksum e abra `app-debug.apk` no telefone, permitindo instalação por essa origem. Alternativamente:

```sh
sha256sum -c SHA256SUMS
adb install -r app-debug.apk
```

- Pacote: `br.com.pevicasi.nfefacil`, versão `1.0.0` (código 1).
- Android mínimo: 8.0/API 26; target/compile SDK 36.
- APK universal: arm64-v8a, armeabi-v7a, x86 e x86_64.
- SHA-256 do **APK baixado do Actions**: `572f36ed457980d7ef6905d1b508b1ed21ab240c883fe1dac772ff58a3fa0584`.
- Assinatura de desenvolvimento, adequada para instalação e teste. Não é uma publicação na Play Store. A assinatura de builds locais pode diferir da de CI.
- Artefato retido por 30 dias. Depois, use **Actions → Android APK → Run workflow** para gerar outro.

Relatórios da mesma execução: `validation-reports` (testes web, capturas, testes unitários e lint) e `device-test-reports` (7 testes de integração).

### Limites da validação histórica

Nenhum aparelho físico foi conectado. Foco, zoom, flash e qualidade de leitura em papel real variam por hardware e iluminação; a automação confirma execução dos modelos e fluxos, não desempenho em todas as câmeras. Impressão física, geolocalização real, captcha e consultas governamentais autenticadas não foram exercitados. As páginas de preços preservam as dependências e a falha de mensagem de erro pré-existente detalhadas em [AUDITORIA.md](AUDITORIA.md).

A primeira execução de CI falhou no provisionamento por solicitar o pacote SDK removido `tools`; a configuração foi corrigida para `platform-tools`, e o workflow final acima passou. Execuções intermediárias canceladas por commits posteriores não são usadas como evidência de sucesso. A documentação posterior não altera o código do APK testado.
