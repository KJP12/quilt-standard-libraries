/*
 * Copyright 2021 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.qsl.resource.loader.mixin;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ServerResourceManager;
import net.minecraft.server.MinecraftServer;

import org.quiltmc.qsl.resource.loader.api.ResourceLoaderEvents;
import org.quiltmc.qsl.resource.loader.impl.ModNioResourcePack;
import org.quiltmc.qsl.resource.loader.impl.ModResourcePackProvider;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	@Shadow
	private ServerResourceManager serverResourceManager;

	@Redirect(method = "loadDataPacks", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
	private static boolean onCheckDisabled(List<String> list, Object o, ResourcePackManager resourcePackManager) {
		var profileName = (String) o;
		if (list.contains(profileName)) {
			return true;
		}

		ResourcePackProfile profile = resourcePackManager.getProfile(profileName);

		if (profile.getSource() == ModResourcePackProvider.PACK_SOURCE_MOD_BUILTIN) {
			ResourcePack pack = profile.createResourcePack();
			// Prevents automatic load for built-in data packs provided by mods that are not enabled by default.
			return pack instanceof ModNioResourcePack modResourcePack && !modResourcePack.getActivationType().isEnabledByDefault();
		}

		return false;
	}

	@Inject(method = "reloadResources", at = @At("HEAD"))
	private void onReloadResourcesStart(Collection<String> collection, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		ResourceLoaderEvents.START_DATA_PACK_RELOAD.invoker().onStartDataPackReload((MinecraftServer) (Object) this,
				this.serverResourceManager);
	}

	@Inject(method = "reloadResources", at = @At("TAIL"))
	private void onReloadResourcesEnd(Collection<String> collection, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		cir.getReturnValue().handleAsync((value, throwable) -> {
			ResourceLoaderEvents.END_DATA_PACK_RELOAD.invoker().onEndDataPackReload((MinecraftServer) (Object) this,
					this.serverResourceManager, throwable);
			return value;
		}, (MinecraftServer) (Object) this);
	}
}