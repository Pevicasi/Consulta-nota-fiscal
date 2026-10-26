# Investigação de instalação e Google Play Protect

## Evidências e limites

A incompatibilidade de instalação foi reproduzida pela inspeção dos certificados e metadados dos APKs (ver [assinatura](../android/ASSINATURA.md)). Ela é uma verificação do Package Manager, diferente de uma classificação do Play Protect. Os APKs antigos são debug, distribuídos por download fora da Play Store, com identidade de assinatura variável. Isso não permite concluir qual alerta apareceu nem que houve detecção de malware.

O responsável informou que, nas versões 1.0.0 e 1.0.1, apareceu: “O app foi bloqueado para proteger seu dispositivo. O Play Protect nunca viu um app desse desenvolvedor. Ele pode não ser seguro.” Havia a opção “Instalar assim mesmo”, sem indicação explícita de malware. Essa opção foi relatada como evidência; não é uma recomendação para contornar o bloqueio.

O texto identifica um bloqueio por desenvolvedor/identidade ainda desconhecido pelo serviço, sem uma classificação explícita de comportamento nocivo. Isso não é prova de ausência de malware nem permite declarar o alerta falso positivo. É diferente do conflito de pacote/assinatura também relatado na 1.0.1. A identidade debug variável entre builds prejudica continuidade e rastreabilidade; não se afirma que ela seja a única causa do alerta. Uma assinatura permanente não garante remoção do aviso e não equivale a uma análise/aprovação da Google. Não foi disponibilizado aparelho físico, versão do Play Protect ou análise dinâmica para reproduzir a decisão do serviço.

## Revisão realizada no projeto

- Permissões declaradas: câmera, internet e localização aproximada/precisa. Câmera e localização são opcionais, solicitadas em tempo de uso; a leitura CODE 128/OCR usa modelos embarcados sem envio de imagens. Internet serve às páginas de preços/portais; localização é usada somente mediante solicitação de geolocalização da origem local autorizada.
- A inspeção do APK QA com `aapt dump permissions` confirmou também `ACCESS_NETWORK_STATE` (dependências) e uma permissão própria `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de nível signature, adicionada pelo AndroidX. Não há permissões de SMS, contatos, administração do dispositivo, instalação de pacotes, sobreposição de tela ou serviço de acessibilidade.
- Foi inspecionado o **manifest mesclado do APK**, além do manifesto-fonte. Das Activities, somente a principal é exportada; o scanner e a GoogleApiActivity são internos. ML Kit, inicializadores e Google DataTransport adicionam componentes internos. Há um `androidx.profileinstaller.ProfileInstallReceiver` exportado, protegido por `android.permission.DUMP` (sistema/shell); isso não é uma permissão de instalação de APK nem acesso irrestrito por apps comuns. Não se removeu esse componente AndroidX apenas para ocultar sua presença.
- Não foi realizada captura dinâmica de tráfego dos SDKs. A presença de Google DataTransport e o funcionamento offline do scanner não permitem prometer ausência de toda telemetria quando a rede está ativa. A revisão de manifesto/permissões não substitui análise comportamental do APK e do alerta específico.
- WebView sem acesso arbitrário a arquivos/conteúdo, sem mixed content/HTTP, sem popups JavaScript e com ponte restrita ao frame principal da origem local. Navegação externa abre outro app. Corrigida a checagem de geolocalização por prefixo para igualdade exata de origem, com teste de origem impostora.
- Sem downloads/carregamento de DEX remoto ou empacotador/obfuscador para ocultar comportamento. Versões das dependências Android/ML Kit permanecem fixadas; Gradle Wrapper conserva o checksum. TLS, checksums e verificações de assinatura não foram desativados. Modelos e binários continuam vindo das dependências originais Google/AndroidX.
- Distribuição preparada como release não depurável, com chave persistente em Secrets, certificado público fixado e conferência do APK efetivamente gerado. APK QA é identificado separadamente como teste. Isso melhora rastreabilidade e identidade; não certifica ausência de vulnerabilidades nas dependências nem promete reputação na Google.
- Backup automático de dados fiscais permanece desabilitado. Nenhum comando limpa dados ou desinstala o app no aparelho do usuário.

## Como investigar um alerta real

Registre o texto exato e a classificação do alerta, versão do app, origem do download e SHA-256 do APK. Compare com `SHA256SUMS` e a impressão do certificado aprovado. Não envie documentos fiscais, XMLs ou senhas para serviços públicos de análise. Um APK modificado, com checksum/certificado diferente, deve ser separado da investigação do artefato oficial.

Não desative nem contorne o Play Protect. Para uma detecção de app nocivo, mantenha o bloqueio até a análise do alerta e do APK correspondente. Alertas sobre apps desconhecidos/verificação e detecções de comportamento nocivo exigem diagnósticos diferentes; nenhuma foi presumida neste relatório.

Assinar o APK comprova continuidade da identidade criptográfica; não equivale à verificação da identidade civil/organizacional do desenvolvedor pela Google. Eventual cadastro e verificação oficial na Android Developer Console ou Play Console exigem ação do responsável e observância das regras vigentes da plataforma/região; não foram efetuados pelo build. Consulte também https://developer.android.com/developer-verification .

Para distribuição ampla, avalie publicação e verificações oficiais pela Play Console e o fluxo oficial de recurso quando uma detecção for considerada incorreta. Cadastro, políticas, titularidade, taxas e publicação dependem do responsável; não foram realizados nesta tarefa. Publicação oficial também não garante ausência de alertas.

Referências oficiais:

- https://support.google.com/googleplay/answer/2812853?hl=pt-BR
- https://developer.android.com/studio/publish/app-signing
- https://support.google.com/googleplay/android-developer/answer/9888380
