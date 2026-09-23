#!/usr/bin/env python3
import xml.etree.ElementTree as ET
import re
import os

SRC_PATH = "app/shared/app-lang/src/androidMain/res/values/strings.xml"
DEST_PATH = "app/shared/app-lang/src/androidMain/res/values-es/strings.xml"

# Common Latin Spanish vocabulary and phrase mappings
REPLACEMENTS = [
    # UI / Navigation
    (r"\bDevelopers List\b", "Lista de Desarrolladores"),
    (r"\bAcknowledgements\b", "Agradecimientos"),
    (r"\bSettings\b", "Ajustes"),
    (r"\bPlayback History\b", "Historial de Reproducción"),
    (r"\bNo playback history\b", "No hay historial de reproducción"),
    (r"\bUnknown anime\b", "Anime desconocido"),
    (r"\bUnknown episode\b", "Episodio desconocido"),
    (r"\bCover of\b", "Portada de"),
    (r"\bEp\.\b", "Ep."),
    (r"\bUnknown\b", "Desconocido"),
    (r"\bselected\b", "seleccionados"),
    (r"\bSelect playback records\b", "Seleccionar registros de reproducción"),
    (r"\bExit selection\b", "Salir de la selección"),
    (r"\bSelect all\b", "Seleccionar todo"),
    (r"\bDelete selected playback records\b", "Eliminar registros seleccionados"),
    (r"\bDelete playback records\?\b", "¿Eliminar registros de reproducción?"),
    (r"\bSelected records will be removed from this device and synced to your account\.\b", "Los registros seleccionados se eliminarán de este dispositivo y se sincronizarán con tu cuenta."),
    (r"\bPlayback history synced\b", "Historial de reproducción sincronizado"),
    (r"\bpending sync items\b", "elementos pendientes de sincronizar"),
    (r"\bSync Status\b", "Estado de sincronización"),
    (r"\bAll playback history changes are synced\b", "Todos los cambios del historial están sincronizados"),
    (r"\bPending changes\b", "Cambios pendientes"),
    (r"\bUpdate progress\b", "Actualizar progreso"),
    (r"\bDelete record\b", "Eliminar registro"),
    (r"\bEpisode ID\b", "ID de episodio"),
    (r"\bDelete pending sync item\b", "Eliminar elemento pendiente"),
    (r"\bDelete all pending sync items\b", "Eliminar todos los elementos pendientes"),
    
    # Categories & Tabs
    (r"\bApp & UI\b", "Aplicación e Interfaz"),
    (r"\bData Source & Playback\b", "Fuentes de Datos y Reproducción"),
    (r"\bNetwork & Storage\b", "Red y Almacenamiento"),
    (r"\bOthers\b", "Otros"),
    (r"\bDebug mode enabled\b", "Modo de depuración activado"),
    (r"\bAppearance\b", "Apariencia"),
    (r"\bTheme & Colors\b", "Tema y Colores"),
    (r"\bPlayer & Danmaku Filter\b", "Reproductor y Filtros Danmaku"),
    (r"\bData Source Management\b", "Administración de Fuentes"),
    (r"\bViewing Preferences\b", "Preferencias de Visualización"),
    (r"\bServer Region\b", "Región del Servidor"),
    (r"\bProxy\b", "Proxy"),
    (r"\bBitTorrent\b", "BitTorrent"),
    (r"\bAuto Cache\b", "Caché Automática"),
    (r"\bStorage\b", "Almacenamiento"),
    (r"\bSettings Backup\b", "Copia de Seguridad de Ajustes"),
    (r"\bLogs\b", "Registros"),
    (r"\bOpen log directory\b", "Abrir carpeta de registros"),
    (r"\bApp Updates\b", "Actualizaciones de la App"),
    (r"\bAbout\b", "Acerca de"),
    (r"\bDebug\b", "Depuración"),
    (r"\bDebug mode status\b", "Estado del modo de depuración"),
    (r"\bDebug mode is ON, click to disable\b", "Modo de depuración ACTIVO, toca para desactivar"),
    (r"\bDebug mode\b", "Modo de depuración"),
    (r"\bItem Episode Selection\b", "Selección de Episodios"),
    (r"\bShow all episodes\b", "Mostrar todos los episodios"),
    (r"\bMetered network information\b", "Información de redes con uso medido"),
    (r"\bNew user guide settings\b", "Ajustes de guía de bienvenida"),
    (r"\bEnter new user tutorial\b", "Iniciar tutorial de bienvenida"),
    (r"\bReset new user guide status\b", "Restablecer guía de nuevo usuario"),
    (r"\bNew user guide status has been reset\b", "Se ha restablecido la guía de nuevo usuario"),
    (r"\bSend feedback\b", "Enviar comentarios"),
    (r"\bAni official website\b", "Sitio web oficial de Ani"),
    (r"\bVersion\b", "Versión"),
    (r"\bRelease notes\b", "Notas de la versión"),
    (r"\bWebsite\b", "Sitio web"),
    (r"\bFeedback & suggestions\b", "Comentarios y sugerencias"),
    (r"\bSource code\b", "Código fuente"),
    (r"\bChat groups\b", "Grupos de chat"),
    (r"\bQQ Group\b", "Grupo de QQ"),
    (r"\bMain contributors\b", "Colaboradores principales"),
    (r"\bProject initiator\b", "Iniciador del proyecto"),
    (r"\bDaily maintenance\b", "Mantenimiento diario"),
    (r"\bOutstanding contributors \(alphabetically\)\b", "Colaboradores destacados (alfabéticamente)"),
    (r"\bServer-side development\b", "Desarrollo del servidor"),
    (r"\bBangumi upstream contributions\b", "Contribuciones a Bangumi"),
    (r"\bIcon design\b", "Diseño de iconos"),
    (r"\bOfficial website development\b", "Desarrollo del sitio web oficial"),
    (r"\bContributor\b", "Colaborador"),
    (r"\bMachine learning R&D\b", "I+D de Aprendizaje Automático"),
    (r"\bOrganization\b", "Organización"),
    (r"\bView more on GitHub\b", "Ver más en GitHub"),
    (r"\bNo comments yet\b", "Aún no hay comentarios"),

    # Player & Controls
    (r"\bVideo Player\b", "Reproductor de Video"),
    (r"\bPlayer\b", "Reproductor"),
    (r"\bDanmaku\b", "Danmaku"),
    (r"\bBullet comments\b", "Comentarios Danmaku"),
    (r"\bSubtitle\b", "Subtítulo"),
    (r"\bSubtitles\b", "Subtítulos"),
    (r"\bAudio\b", "Audio"),
    (r"\bSpeed\b", "Velocidad"),
    (r"\bHardware decoding\b", "Decodificación por hardware"),
    (r"\bSuper resolution\b", "Super-resolución"),
    (r"\bImage enhancement\b", "Mejora de imagen"),
    (r"\bVolume\b", "Volumen"),
    (r"\bBrightness\b", "Brillo"),
    (r"\bFullscreen\b", "Pantalla completa"),
    (r"\bExit fullscreen\b", "Salir de pantalla completa"),
    (r"\bPlay\b", "Reproducir"),
    (r"\bPause\b", "Pausar"),
    (r"\bStop\b", "Detener"),
    (r"\bNext episode\b", "Siguiente episodio"),
    (r"\bPrevious episode\b", "Episodio anterior"),

    # Data Source & Search
    (r"\bData Source\b", "Fuente de Datos"),
    (r"\bData Sources\b", "Fuentes de Datos"),
    (r"\bOnline\b", "En línea"),
    (r"\bTest\b", "Probar"),
    (r"\bTest connection\b", "Probar conexión"),
    (r"\bEnabled\b", "Habilitado"),
    (r"\bDisabled\b", "Deshabilitado"),
    (r"\bEnable\b", "Habilitar"),
    (r"\bDisable\b", "Deshabilitar"),
    (r"\bResolution\b", "Resolución"),
    (r"\bDefault\b", "Predeterminado"),
    (r"\bSearch URL\b", "URL de búsqueda"),
    (r"\bSearch\b", "Buscar"),
    (r"\bSearching\b", "Buscando"),
    (r"\bFilter\b", "Filtrar"),
    (r"\bFilters\b", "Filtros"),

    # Actions
    (r"\bCancel\b", "Cancelar"),
    (r"\bConfirm\b", "Confirmar"),
    (r"\bSave\b", "Guardar"),
    (r"\bDelete\b", "Eliminar"),
    (r"\bRemove\b", "Quitar"),
    (r"\bEdit\b", "Editar"),
    (r"\bCopy\b", "Copiar"),
    (r"\bCopied\b", "Copiado"),
    (r"\bClose\b", "Cerrar"),
    (r"\bRetry\b", "Reintentar"),
    (r"\bBack\b", "Atrás"),
    (r"\bDone\b", "Listo"),
    (r"\bOK\b", "Aceptar"),
    (r"\bApply\b", "Aplicar"),
    (r"\bClear\b", "Limpiar"),
    (r"\bReset\b", "Restablecer"),
    (r"\bSuccess\b", "Éxito"),
    (r"\bFailed\b", "Falló"),
    (r"\bError\b", "Error"),
    (r"\bLoading\b", "Cargando"),
    (r"\bConnecting\b", "Conectando"),
    (r"\bConnected\b", "Conectado"),
    (r"\bDisconnected\b", "Desconectado"),
    (r"\bDownload\b", "Descargar"),
    (r"\bDownloading\b", "Descargando"),
    (r"\bDownloaded\b", "Descargado"),
    (r"\bCache\b", "Caché"),
    (r"\bCaching\b", "Almacenando en caché"),

    # Subject details
    (r"\bEpisodes\b", "Episodios"),
    (r"\bEpisode\b", "Episodio"),
    (r"\bDetails\b", "Detalles"),
    (r"\bComments\b", "Comentarios"),
    (r"\bComment\b", "Comentario"),
    (r"\bCharacters\b", "Personajes"),
    (r"\bCharacter\b", "Personaje"),
    (r"\bStaff\b", "Personal"),
    (r"\bRelations\b", "Relacionados"),
    (r"\bWatching\b", "Viendo"),
    (r"\bCompleted\b", "Completado"),
    (r"\bOn Hold\b", "En espera"),
    (r"\bDropped\b", "Abandonado"),
    (r"\bPlan to Watch\b", "Por ver"),
    (r"\bRating\b", "Puntuación"),
    (r"\bRank\b", "Clasificación"),
    (r"\bAir date\b", "Fecha de emisión"),
]

def translate_text(text, name):
    if not text:
        return text
    

    
    res = text
    for pattern, repl in REPLACEMENTS:
        res = re.sub(pattern, repl, res)
    return res

def main():
    os.makedirs(os.path.dirname(DEST_PATH), exist_ok=True)
    
    with open(SRC_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()
        
    out_lines = []
    string_pattern = re.compile(r'^(?P<indent>\s*)<string\s+name="(?P<name>[^"]+)"(?P<extra>[^>]*)>(?P<content>.*)</string>(?P<trail>\s*)$')
    
    translated_count = 0
    total_count = 0
    
    for line in lines:
        m = string_pattern.match(line)
        if m:
            total_count += 1
            indent = m.group("indent")
            name = m.group("name")
            extra = m.group("extra")
            content = m.group("content")
            trail = m.group("trail")
            
            if 'translatable="false"' in extra:
                out_lines.append(line)
                continue
                
            tr = translate_text(content, name)
            if tr != content:
                translated_count += 1
                
            out_lines.append(f'{indent}<string name="{name}"{extra}>{tr}</string>{trail}\n')
        else:
            out_lines.append(line)
            
    with open(DEST_PATH, "w", encoding="utf-8") as f:
        f.writelines(out_lines)
        
    print(f"Generated {DEST_PATH}: {translated_count}/{total_count} strings translated")

if __name__ == "__main__":
    main()
