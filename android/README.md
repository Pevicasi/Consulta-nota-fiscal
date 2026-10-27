# NF-e Fácil para Android

## Política atual de entrega — decisão do proprietário na issue #1

O APK pessoal é **NF-e Fácil**, pacote **br.com.pevicasi.nfefacil**, versão **1.1.0**, código **3**, com assinatura debug automática. Não há app `.qa` como entrega nem exigência de Secrets/chave permanente para esse uso. A assinatura debug pode mudar entre runners; atualização sobre assinatura diferente pode falhar e exigir migração manual, com risco de perda de dados. Nunca desinstalar ou apagar dados automaticamente. Não contornar Play Protect nem prometer eliminar seu aviso.

Consulte a [auditoria atual](../docs/AUDITORIA_1_1_0.md). Os trechos antigos sobre “NF-e Fácil (teste)” abaixo são históricos e foram substituídos por esta decisão. No Actions, o artefato pessoal chama-se `nfe-facil-1.1.0-<SHA>`, contendo `app-debug.apk`, checksum, certificado e medição de tamanho. APK de instrumentação é exclusivamente interno.

O build padrão permanece universal para compatibilidade e para o emulador x86_64 do CI. Somente após confirmar a ABI do aparelho, use `./gradlew -PpersonalAbi=arm64-v8a assembleDebug` (ou `armeabi-v7a`) ou a entrada `personal_abi` do workflow manual. Isso filtra apenas bibliotecas nativas; não remove ML Kit, OCR, HTMLs ou testes. Nunca escolher arm64 por suposição. O CI compara APK bruto universal e candidato em `APK-SIZE.json`, mantendo a instrumentação universal. Uma medição local não comprova um APK publicado.

Aplicativo Android 8.0+ (API 26), separado do site, com CameraX e modelos **embarcados** do Google ML Kit (CODE 128 e OCR latino). O leitor funciona sem internet, inclusive na primeira abertura. Os XMLs, cálculos, abas, colunas, preços e histórico usam os mesmos HTMLs do site, copiados automaticamente por `syncSite` durante o build. Não há servidor remoto para abrir as notas nem duplicação manual do site.

## Compilar e testar

Requisitos: JDK 17 ou 21, Android SDK platform 36 e build-tools 35.0.0.

```sh
cd android
# ANDROID_HOME deve apontar para o SDK, ou use local.properties (não versionado).
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
# Com emulador/dispositivo conectado:
./gradlew connectedDebugAndroidTest
```

O APK assinado com chave de desenvolvimento fica em `app/build/outputs/apk/debug/app-debug.apk`. Instale por `adb install -r app/build/outputs/apk/debug/app-debug.apk` ou abra o arquivo no telefone e permita a instalação por essa origem. Esta assinatura serve para instalação e testes; publicação na Play Store exige uma chave de produção guardada pelo responsável. Builds de CI podem usar chaves de debug diferentes; para atualizar um APK com assinatura diferente é necessário desinstalar a versão anterior (apagando seus dados).

No GitHub, abra **Actions → Android APK → execução concluída → Artifacts → nfe-facil-qa-… (teste) ou nfe-facil-release-… (distribuição aprovada)**. Extraia o ZIP e instale `app-debug.apk`; o arquivo `SHA256SUMS` permite conferir a integridade. Os artefatos expiram após 30 dias; execute novamente por **Run workflow** se necessário. Não é criada uma release automaticamente.

## Assinatura, instalação e Play Protect — 1.1.0

O CI normal gera **NF-e Fácil (teste)** no pacote separado `br.com.pevicasi.nfefacil.qa`, para não conflitar com o app instalado. O release de distribuição mantém o pacote original e só é gerado pelo workflow manual com `distribution=true`, após provisionar uma chave permanente e a impressão de certificado aprovada. Consulte [assinatura, configuração segura e migração](ASSINATURA.md) e [investigação de Play Protect](../docs/PLAY_PROTECT.md). Um APK QA não comprova que a distribuição permanente foi provisionada.

O leitor relê regiões sobrepostas com o mesmo ML Kit quando há códigos/potenciais regiões, pois o SDK pode fundir códigos alinhados no quadro completo. O número de resultados do SDK não é uma contagem garantida de códigos físicos. Ele considera todos os CODE 128 válidos, valida a estrutura fiscal além do DV e aplica a mesma política ao OCR. Havendo várias chaves, escolha explicitamente a nota no seletor antes de confirmar; nenhum primeiro código é assumido. A moldura acompanha exclusivamente essa escolha. Validação estrutural não comprova autorização da NF-e na SEFAZ. Testes sintéticos não substituem amostra física da nota com vários códigos.

## Scanner 1.0.1

A moldura acompanha as caixas reais do detector, com suavização e indicação verde para chave válida. A guia cinza é apenas orientativa quando não há detecção; caixas antigas expiram. As coordenadas usam o viewport compartilhado do CameraX, rotação do frame e transformação da prévia, incluindo câmera frontal. Para reduzir movimento, desative as animações nas configurações de acessibilidade do Android. O zoom permanece manual, por pinça ou barra.

No diálogo **Chave encontrada**, confira os 44 dígitos selecionáveis e toque em **Confirmar chave**. **Ler novamente** retorna à câmera sem enviar uma chave. Se houver várias chaves, escolha uma no seletor antes de confirmar. A confirmação é restaurada na recriação da tela.

## Uso e paridade

- **Notas:** importar um ou vários XMLs pelo seletor do Android; mesmos cálculos de ST, margem, abas, busca, seleção/ordem de colunas e sessão local do site.
- **Ler chave:** CameraX, foco automático e por toque, zoom por pinça/barra, lanterna quando suportada, câmera frontal/traseira, CODE 128 e OCR contínuos. Confirme a chave reconhecida; o dígito verificador módulo 11 é obrigatório. Nenhuma imagem é enviada a um servidor.
- **Foto / recorte** ou **Galeria:** arraste a seleção e o canto inferior direito; toque em **Ler recorte**. O OCR analisa linhas separadamente para não unir CNPJ, protocolo e outros números. Sem chave válida, ajuste a foto e tente novamente. Cancelar não altera a nota.
- Clipboard usa o Android; CSV usa o seletor **Salvar como**; **Imprimir** abre a impressão Android, inclusive salvar PDF. Nenhuma permissão ampla de armazenamento é solicitada.
- **Preços:** acesso às versões 17, 16 e 15. Dependem das mesmas APIs públicas, conectividade, CORS, geolocalização e, na Consulta 2, autenticação do serviço RS. Não há token incluído no aplicativo. Links NF-e/ScanApp/mapas abrem em aplicativo externo.
- A leitura da chave não baixa a NF-e: para consultar os itens, importe seu XML; o portal oficial continua externo e pode exigir captcha.

A origem local HTTPS usa WebViewAssetLoader; a ponte nativa aceita mensagens somente do frame principal nessa origem. Navegação remota é aberta externamente. Acesso `file://`, conteúdo local arbitrário, tráfego HTTP sem TLS e backup automático dos dados fiscais estão desativados. Modelos ML Kit empacotados dispensam scripts OCR externos no app.

Permissão negada/câmera ausente: galeria continua disponível. Sem flash: lanterna desativada. A câmera e a lanterna são liberadas ao sair. Use um Android System WebView atualizado. Dados da sessão ficam no armazenamento local do aplicativo e são apagados ao limpar dados/desinstalar. O comportamento de retenção do site foi preservado.

Consulte [resultado do build e APK verificado](../docs/VALIDACAO.md), [auditoria](../docs/AUDITORIA.md) e [registro de mudanças](../CHANGELOG.md). Testes automatizados não substituem a validação de foco, lanterna, qualidade do OCR e impressão em dispositivos físicos.
