/**
 * Identidade e acesso — usuários, papéis, permissões e tokens.
 *
 * <p>Senha em Argon2id, access token JWT RS256 de 15 minutos, refresh opaco com
 * rotação e detecção de reuso. A autorização é por permissão fina
 * ({@code contract:renew}), nunca por papel: assim, mudar quem pode o quê é uma
 * linha no banco, não um deploy.
 *
 * <p>Fase 2 do roadmap.
 */
package com.caiocodes.vigencia.iam;
