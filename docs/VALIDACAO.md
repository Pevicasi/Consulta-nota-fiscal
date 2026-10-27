# Resultados verificados

## Atualização 1.2.0 — fontes e modo noturno

A atualização implementa as duas melhorias na mesma entrega, com APK universal pessoal, identidade original e debug automático. O escopo, comandos e testes estão em [APARENCIA.md](APARENCIA.md). Os resultados do GitHub Actions e o artefato do commit final são registrados na [issue #1](https://github.com/Pevicasi/Consulta-nota-fiscal/issues/1). Um build local não substitui essa validação. Links e resultados 1.1.0/QA abaixo são históricos.

## Histórico: auditoria integral 1.1.0 — estado anterior à atualização 1.2.0

O relatório vigente está em [AUDITORIA_1_1_0.md](AUDITORIA_1_1_0.md). Os links QA e os APKs 1.0.x abaixo são históricos e **não são a entrega atual**. A decisão do proprietário autoriza um único app pessoal com nome e pacote originais, assinatura debug automática e sem Secrets de distribuição.

O commit-base `54db98e7f3ee22252ecdb5535a64d63f8d9ef6e8` teve [Actions 40439251418](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40439251418) verde, mas continha falha reproduzida de visibilidade do desconto. O artefato `12706105790` desse run não comprova as correções desta auditoria e não deve ser oferecido como versão corrigida. O resultado do CI do novo commit e seu APK precisam ser conferidos antes de aprovação. Um build local não substitui essa verificação.

## 1.1.0 — Continuação da issue #1: QA aprovado; distribuição permanente pendente

Código testado: [`001e91894af7329ea5f19dd338a6b44293fcb23f`](https://github.com/Pevicasi/Consulta-nota-fiscal/commit/001e91894af7329ea5f19dd338a6b44293fcb23f). [Workflow QA concluído com sucesso](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40415457546).

[**Artefato QA 1.1.0**](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40415457546/artifacts/12695865664): pacote `br.com.pevicasi.nfefacil.qa`, versão `1.1.0-qa`, código `3`. Instala em identidade separada do app original. **Não é o release com chave permanente e não comprova resolução definitiva das atualizações de distribuição.** O artefato traz essa identificação, `SHA256SUMS` e o certificado público. Não se recomenda contornar o Play Protect para instalá-lo.

| Verificação | Resultado observado |
|---|---|
| Site / ponte web | 8 testes passaram; 0 falhas; 0 ignorados |
| Validação fiscal unitária | 8 testes passaram; 0 falhas; 0 ignorados |
| Política de assinatura | 5 testes Python passaram, com fixtures isoladas; rejeição de configuração ausente/incorreta, proteção do arquivo e ausência de vazamento de senha/base64 nos resultados |
| Integração Android | **19 testes passaram; 0 falhas; 0 erros; 0 ignorados**, reportados nas anotações públicas do Actions |
| Imagens com vários códigos | Boleto ITF sintético, CODE 128 não fiscal com DV, QR contendo outra chave fiscal, chave NF-e correta, NFC-e, código com DV inválido e cópias repetidas |
| Recuperação de chave oculta | Mesmo ML Kit relendo regiões sobrepostas recuperou a NF-e omitida no quadro completo; código de pagamento/QR não foi selecionado |
| Ambiguidade e animação | Duas chaves válidas exigem escolha; sem alvo implícito; coordenadas das regiões voltam ao quadro original; moldura cabe num código individual, não na região fundida |
| OCR e navegação | OCR com múltiplas chaves segue a mesma validação; confirmação, retorno, recriação da Activity, XML/CSV e origem impostora de geolocalização cobertos |
| QA / APK no Actions | Build, lint e verificação de assinatura v2, pacote separado, versão e identidade dos quatro HTMLs com o repositório passaram |
| Release local de validação | `testReleaseUnitTest lintRelease assembleRelease assembleReleaseAndroidTest` passaram com **fixture de dois dias**, não uma chave permanente; assinatura/pacote/versão/release não depurável conferidos. APK de instrumentação usou o mesmo certificado da fixture. Chave e candidato de distribuição da fixture removidos |
| Falha segura | `assembleRelease` sem assinatura provisionada falhou como esperado, sem criar chave de fallback |
| Histórico / artefatos | Main atualizada sem PR/rebase/reset; site original inalterado. Nenhum APK anterior foi excluído nesta tarefa; aguardam aprovação da distribuição permanente |

A execução inicial reprovou duas imagens. [Diagnóstico detalhado](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40412563570) demonstrou que o SDK podia fundir códigos parecidos/alinhados e omitir a chave correta mesmo no modo CODE 128. Foi implementada releitura de regiões com o próprio ML Kit, sem substituir o leitor por outro. O teste passou a exigir as identidades fiscais corretas e a geometria individual, sem presumir que o SDK forneça um objeto por código físico. Nenhuma assertion de chave correta, formato, DV, ambiguidade ou localização foi desativada.

A página pública do Actions confirmou o workflow verde e os contadores executados. O SHA-256 **do ZIP do artefato publicado** é `ddeb473ba6c3e2a6f8b686d65dfc254cce4197a5a7b3547c231f789129a63dcc`. Não confundir com o SHA-256 do APK contido no ZIP. Nesta sessão, a API GitHub retornou HTTP 403: não foi possível baixar e conferir independentemente o artefato nem consultar nomes/bindings dos Secrets do repositório. A verificação do APK publicado foi executada pelo job do CI; o APK QA local também foi verificado.

### Pendências de distribuição e configuração

O job de distribuição é manual e não foi executado com identidade permanente. A existência de Secrets GitHub permanece **não verificável desta sessão**; a ausência de variáveis na máquina não prova ausência de Secrets no GitHub. Para concluir, reutilize/provisione o keystore permanente sob guarda do responsável, os quatro Secrets e a variável pública de certificado descritos em [ASSINATURA.md](../android/ASSINATURA.md), e execute o workflow com `distribution=true`. Não envie arquivo/base64/senhas no chat. Restaure o acesso efetivo à API/downloads nas configurações do ambiente para permitir acompanhamento, conferência independente e atualização da issue. O rascunho de rede foi salvo; publicação/aplicação efetiva não foi confirmada.

O conflito de instalação e o aviso do Play Protect foram separados em [PLAY_PROTECT.md](PLAY_PROTECT.md). O responsável informou bloqueio por desenvolvedor ainda desconhecido nas versões 1.0.0/1.0.1, sem classificação explícita de malware. Não foi desativada proteção, não houve teste do Play Protect em aparelho físico e não se promete que um certificado permanente elimine o alerta. Verificação de identidade/publicação oficial do desenvolvedor depende do responsável.

Limites: não houve amostra física da nota com muitos códigos, medição de latência/consumo em aparelho real, teste de câmera/foco/flash físico nem migração no aparelho do usuário. As releituras são limitadas a 14 regiões, sequenciais, fora da UI e condicionadas à presença de regiões detectadas/potenciais. Validação estrutural não prova autorização da nota na SEFAZ. Os testes instrumentados verificam imagens sintéticas, transformações e fluxos do app; não garantem detecção universal de códigos desfocados/ocultos.

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
