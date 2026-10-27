# Auditoria integral — NF-e Fácil 1.1.0 / issue #1

**PENDENTE.** Este relatório distingue código, execução local, histórico público do CI e validação ainda necessária. Não aprova APK antigo/QA. Requisitos: [issue #1, incluindo os dois comentários de auditoria](https://github.com/Pevicasi/Consulta-nota-fiscal/issues/1). A última decisão de assinatura autoriza debug automático para uso pessoal e substitui a exigência de chave permanente.

## Evidência de partida

- Main remota e checkout inicial: `54db98e7f3ee22252ecdb5535a64d63f8d9ef6e8`, após `baaf453` (desconto), `f911f68` (APK pessoal) e `ae539ba` (identidade original).
- [Actions 40439251418](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40439251418), do commit-base: página pública informa Success e 19 instrumentados, 0 falhas/erros/ignorados. Artefato pessoal `12706105790`, nome `nfe-facil-1.1.0-54db98e7f3ee22252ecdb5535a64d63f8d9ef6e8`. **Histórico, não valida o código corrigido nesta auditoria.**
- [Pages 40439251591](https://github.com/Pevicasi/Consulta-nota-fiscal/actions/runs/40439251591), do mesmo commit-base: Success. A publicação das novas correções exige nova conferência.
- APK local do commit-base: **69.077.658 bytes**, SHA-256 `d16c06154de3cd28b87106f745312b1fb637e6332c4858f8d2708223fe8f8be7`. Não é o download verificado do Actions. Foi preservado fora do checkout para comparação.

## Conferência por requisito

| Requisito | Estado e evidência | Limite / ação restante |
|---|---|---|
| Nome NF-e Fácil, pacote original, 1.1.0/código 3; sem app QA de entrega | CONFIRMADO no código (`android/app/build.gradle`) e no APK local por `aapt`/`verify-qa.py`; CI normal usa debug automático. Verificador exige nome, pacote, versão, assinatura v2 e ABIs. | Repetir conferência no APK do Actions do commit final. Nome do script `verify-qa.py` é histórico; ele rejeita `.qa` e `-qa`. |
| Não exigir assinatura permanente/Secrets para uso pessoal | CONFIRMADO: build debug não depende de `requireDistributionSigning`. `ASSINATURA.md` e README corrigidos para a decisão vigente. Release permanente permanece opcional. | Debug pode mudar entre runners; não garante upgrade. Não desinstalar nem apagar dados. |
| Play Protect / migração segura | PARCIAL: `docs/PLAY_PROTECT.md`, backup e incompatibilidade documentados; permissões revisadas. | NÃO TESTADO no telefone. Não prometer eliminar alerta, não contornar proteção. |
| CameraX / ML Kit local, chave 44 dígitos e DV | CONFIRMADO no código: `ScannerActivity`, `FiscalScanner`, `NfeKey`; 8 unitários executados localmente. Validação de UF, mês, CNPJ/CPF, modelo 55/65, emissão, número e DV. | Reconhecimento instrumental do código corrigido depende do CI; qualidade em câmera real NÃO TESTADA. Validação não comprova autorização SEFAZ. |
| Todos os resultados, múltiplos códigos, escolha explícita | CONFIRMADO no código: `DetectedKeys` e `MultiBarcodeTest`; 6 testes instrumentados sintéticos existentes, incluídos nos 19 do CI-base. Releituras de regiões limitadas, sem presumir um resultado por código físico. | Não chamar o CI-base de execução dos novos fontes. Não há amostra física do usuário. |
| Moldura animada / candidato selecionado / confirmar / reler | CONFIRMADO no código `ScannerOverlay`, `ScannerActivity`, `ScannerTest`; transformação, expiração, seleção e recriação cobertas no CI-base. | Suavidade, foco, flash, zoom e geometria em aparelho físico NÃO TESTADOS. |
| OCR validado com a mesma política e modelos embarcados | CONFIRMADO no código e testes `AppIntegrationTest`/`MultiBarcodeTest`. Dependências ML Kit mantidas; scanner não depende dos scripts remotos web. | OCR e barcode reais do candidato devem executar no CI; legibilidade de nota física não garantida. |
| Telas, XMLs, notas, preços, ST, acréscimo padrão 20% e configurável | CONFIRMADO pela regressão web local (10 testes no total), incluindo XMLs múltiplos, abas, 20%/50%, colunas, busca, persistência, entradas inválidas e XSS. | APIs governamentais, autenticação RS e localização real NÃO TESTADAS. Erro de status em Consulta 2 corrigido e coberto com rede simulada. |
| CSV, impressão e navegação | CONFIRMADO parcialmente: CSV de navegador e roteamento da ponte nativa exercitados na regressão; retorno Preços→Notas coberto no CI-base. Código Android usa SAF e PrintManager. | Salvar CSV pelo seletor Android, impressão/PDF real e impressora física não foram exercitados ponta a ponta nesta auditoria. |
| Desconto oficial positivo em cartão verde no site e APK | Falha reproduzida antes da correção: `hidden` impedia visibilidade. CORRIGIDO em `index.html`; regressão web passou. Acrescentado `bundledInvoiceShowsOfficialDiscountOnlyWhenPositive` no WebView embarcado. | PARCIAL até executar o novo instrumentado no CI. Não confundir compilar teste com executá-lo. |
| Zero/ausente, itens, não duplicar, não inferir diferença de totais | CONFIRMADO na regressão web: oficial positivo/zero autoritativo, ausência sem desconto, fallback de item, frete/outros ajustes, persistência e acréscimo independente. | Novo teste Android percorre os mesmos cinco casos; execução remota pendente. |
| Site preservado / Pages | Mesmas quatro páginas continuam fonte única do APK; `apk_integrity.py` compara bytes. Mudanças web restritas às correções solicitadas de desconto e status; sem reescrita do site. | Publicação Pages do novo commit precisa ser conferida. |
| Unitários, regressão, lint, segurança | Local: 10 web + 8 Java + 5 Python de política de assinatura; build/debug/lint/APK de instrumentação executados. Manifest sem tráfego HTTP, backup desativado, ponte por origem/frame, acesso file/content desativado; XSS e origem impostora têm testes. | Não constitui pentest ou varredura de vulnerabilidades completa. Instrumentados não executados localmente (sem KVM/dispositivo). CI deve reportar 20 após inclusão do desconto. |
| ABI e otimização de tamanho, preservando OCR/barcode | PARCIAL: `-PpersonalAbi` aceita apenas ARM explicitamente confirmado; padrão universal e instrumentação x86_64 preservados. Workflow permite selecionar ABI. `apk-size.py` mede APK bruto, checksum e conteúdo por ABI. | ABI do telefone ainda não fornecida; não escolher arm64 por suposição. Sem isso não há APK otimizado aprovado nem ganho medido para o dispositivo. |
| GitHub Actions do commit final / APK correto / link real | PENDENTE. O run-base verde não satisfaz a aprovação do novo commit. | Conferir SHA final da main, run ID, status/conclusion, contagens, nome/ID do artefato, `aapt`, assinatura e SHA-256 do APK baixado. API/downloads dependem do acesso efetivo a `api.github.com`. |
| Main sem PR / issue / histórico / não apagar APKs | Correções preparadas diretamente na branch main; CHANGELOG e relatório atualizados. Nenhum artefato anterior foi apagado. Alteração pré-existente de `android/gradlew.bat` preservada e fora do commit da auditoria. | Publicação Git e registro na issue devem ser confirmados por seus resultados; não inferir sucesso de uma tentativa. |

## Tamanho: unidades e origem dos arquivos

O comentário registra QA antigo de **69.077.514 bytes** no artefato `12695865664`; esse número é evidência fornecida na issue, não um APK antigo baixado independentemente nesta auditoria. O APK local-base tem **69.077.658 bytes**, diferença de **144 bytes**, mas identidade/ambiente diferem. Isso não autoriza atribuir aumento ao ambiente nem medir otimização com ZIP de artefato.

No APK local-base, `lib/` contém **61.409.164 bytes**: arm64-v8a **16.045.104**, armeabi-v7a **10.050.200**, x86 **17.725.420**, x86_64 **17.588.440**. Modelos/assets, classes, recursos e overhead completam o APK. Remover x86 apenas de uma entrega ARM pode reduzir tamanho; é necessário compilar, medir e verificar o candidato, não subtrair tamanhos e anunciar ganho como observado.

Comandos reproduzíveis:

```sh
stat -c %s BASELINE.apk CANDIDATO.apk
python3 android/tools/apk-size.py BASELINE.apk CANDIDATO.apk
ANDROID_HOME=/caminho/sdk EXPECTED_VERSION_CODE=3 EXPECTED_ABI=universal \
  python3 android/tools/verify-qa.py CANDIDATO.apk
# EXPECTED_ABI deve coincidir com a ABI confirmada se a entrega for filtrada.
```

## Regra de aprovação

Somente declarar APROVADO depois do CI do commit final concluído com sucesso, teste Android de desconto executado, APK pessoal correto identificado e verificado e link real informado. A otimização específica do aparelho permanece pendente enquanto a ABI for desconhecida. Nenhum artefato antigo/QA será apresentado como solução das correções atuais. Validação física, Play Protect e serviços externos devem continuar explicitamente marcados como não testados.
