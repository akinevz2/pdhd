import * as THREE from "three";
import "./theme.css";

type ProjectSummary = {
  id: number;
  directory: string;
  hasGitRepository: boolean;
  githubName?: string | null;
  githubDescription?: string | null;
};

type TreeNode = {
  name: string;
  relativePath: string;
  directory: boolean;
  children: TreeNode[];
};

type ToolActivityItem = {
  timestamp: string;
  toolName: string;
  argumentsJson: string;
  result: string;
  requestedFiles: string[];
};

type ToolActivityResponse = {
  items: ToolActivityItem[];
};

type FileContentResponse = {
  projectDirectory: string;
  filePath: string;
  content: string;
};

const app = document.getElementById("app") as HTMLDivElement;
const highlightedFiles = new Set<string>();

bootScene();
bootUI();

async function bootUI() {
  app.innerHTML = `
		<section class="left-rail panel">
			<div class="toolbar">
				<strong>Projects</strong>
				<button id="refreshProjects">Refresh</button>
			</div>
			<div id="projectList"></div>
		</section>
		<section class="window-host panel" id="windowHost"></section>
		<section class="right-rail panel">
			<div class="toolbar">
				<strong>Assistant Activity</strong>
				<span><kbd>tool traces</kbd></span>
			</div>
			<div id="activity"></div>
		</section>
	`;

  (document.getElementById("refreshProjects") as HTMLButtonElement).onclick =
    () => renderProjects();

  await Promise.all([renderProjects(), pollActivity(true)]);
  setInterval(() => {
    pollActivity(false).catch(() => {
      // ignore transient errors for polling
    });
  }, 2000);
}

async function renderProjects() {
  const projectList = document.getElementById("projectList") as HTMLDivElement;
  projectList.innerHTML = "Loading projects...";

  try {
    const projects = await api<ProjectSummary[]>("/api/projects");
    if (!projects.length) {
      projectList.innerHTML = "No projects discovered yet.";
      return;
    }

    projectList.innerHTML = "";
    for (const project of projects) {
      const card = document.createElement("div");
      card.className = "window panel";
      card.style.marginBottom = "8px";
      card.innerHTML = `
				<div class="title-bar"><div class="title-bar-text">${escapeHtml(
          project.githubName || project.directory,
        )}</div></div>
				<div class="window-body" style="display:block">
					<p style="margin:4px 0 6px;font-size:12px">${escapeHtml(project.directory)}</p>
					<button data-project-id="${project.id}">Open Explorer</button>
				</div>
			`;
      const btn = card.querySelector("button") as HTMLButtonElement;
      btn.onclick = () => openProjectWindow(project);
      projectList.appendChild(card);
    }
  } catch (e) {
    projectList.innerHTML = "Failed to load projects.";
  }
}

async function openProjectWindow(project: ProjectSummary) {
  const windowHost = document.getElementById("windowHost") as HTMLDivElement;
  const win = document.createElement("section");
  win.className = "window-card window";
  win.style.left = `${100 + Math.floor(Math.random() * 120)}px`;
  win.style.top = `${50 + Math.floor(Math.random() * 100)}px`;

  win.innerHTML = `
		<div class="title-bar win-title">
			<div class="title-bar-text">${escapeHtml(project.directory)}</div>
			<div class="title-bar-controls"><button aria-label="Close"></button></div>
		</div>
		<div class="win-body">
			<aside class="tree-pane"><div class="tree">Loading tree...</div></aside>
			<article class="file-pane"><pre>Select a file to view content.</pre></article>
		</div>
	`;

  const closeBtn = win.querySelector(
    ".title-bar-controls button",
  ) as HTMLButtonElement;
  closeBtn.onclick = () => win.remove();

  makeDraggable(win, win.querySelector(".win-title") as HTMLElement);
  windowHost.appendChild(win);

  const treeHolder = win.querySelector(".tree") as HTMLDivElement;
  const filePane = win.querySelector(".file-pane") as HTMLDivElement;

  try {
    const tree = await api<TreeNode>(`/api/projects/${project.id}/tree`);
    treeHolder.innerHTML = "";
    renderTreeNode(project.id, tree, treeHolder, filePane, project.directory);
  } catch (e) {
    treeHolder.textContent = "Failed to load project tree.";
  }
}

function renderTreeNode(
  projectId: number,
  node: TreeNode,
  container: HTMLElement,
  filePane: HTMLElement,
  projectDirectory: string,
  depth = 0,
) {
  if (!node.relativePath && depth === 0) {
    for (const child of node.children) {
      renderTreeNode(
        projectId,
        child,
        container,
        filePane,
        projectDirectory,
        depth + 1,
      );
    }
    return;
  }

  const row = document.createElement("div");
  row.className = "file-node";
  row.style.marginLeft = `${Math.max(0, depth - 1) * 12}px`;
  row.dataset.path = node.relativePath;

  const key = `${projectDirectory}:${normalize(node.relativePath)}`;
  if (highlightedFiles.has(key)) {
    row.classList.add("highlight");
  }

  row.innerHTML = `<span>${node.directory ? "▸" : "▹"} ${escapeHtml(node.name)}</span>`;

  if (!node.directory) {
    row.onclick = async () => {
      try {
        const file = await api<FileContentResponse>(
          `/api/projects/${projectId}/file?path=${encodeURIComponent(node.relativePath)}`,
        );
        filePane.innerHTML = `<pre>${escapeHtml(file.content)}</pre>`;
      } catch (e) {
        filePane.innerHTML = `<pre>Failed to read file: ${escapeHtml(String(e))}</pre>`;
      }
    };
  }

  container.appendChild(row);

  if (node.directory && node.children?.length) {
    for (const child of node.children) {
      renderTreeNode(
        projectId,
        child,
        container,
        filePane,
        projectDirectory,
        depth + 1,
      );
    }
  }
}

async function pollActivity(initial: boolean) {
  const activity = document.getElementById("activity") as HTMLDivElement;
  const data = await api<ToolActivityResponse>("/api/tool-activity?limit=80");

  highlightedFiles.clear();
  for (const event of data.items) {
    for (const file of event.requestedFiles || []) {
      const normalized = normalize(file);
      const openTitles = Array.from(
        document.querySelectorAll(".window-card .title-bar-text"),
      ) as HTMLElement[];
      for (const title of openTitles) {
        const projectDirectory = title.textContent || "";
        highlightedFiles.add(`${projectDirectory}:${normalized}`);
      }
    }
  }

  document.querySelectorAll(".file-node").forEach((el) => {
    const row = el as HTMLElement;
    const path = normalize(row.dataset.path || "");
    let matched = false;
    const title =
      row.closest(".window-card")?.querySelector(".title-bar-text")
        ?.textContent || "";
    if (highlightedFiles.has(`${title}:${path}`)) {
      matched = true;
    }
    row.classList.toggle("highlight", matched);
  });

  activity.innerHTML = "";
  for (const event of data.items.slice().reverse().slice(0, 40)) {
    const item = document.createElement("div");
    item.className = "activity-item";
    item.innerHTML = `
			<div><strong>${escapeHtml(event.toolName)}</strong> <small>${escapeHtml(
        new Date(event.timestamp).toLocaleTimeString(),
      )}</small></div>
			<div>${escapeHtml((event.requestedFiles || []).join(", ") || "no file paths")}</div>
		`;
    activity.appendChild(item);
  }

  if (!data.items.length && initial) {
    activity.innerHTML = "No tool activity yet.";
  }
}

function makeDraggable(windowEl: HTMLElement, handle: HTMLElement) {
  let active = false;
  let startX = 0;
  let startY = 0;
  let left = 0;
  let top = 0;

  handle.addEventListener("mousedown", (e) => {
    active = true;
    startX = e.clientX;
    startY = e.clientY;
    left = windowEl.offsetLeft;
    top = windowEl.offsetTop;
  });

  window.addEventListener("mousemove", (e) => {
    if (!active) {
      return;
    }
    windowEl.style.left = `${left + (e.clientX - startX)}px`;
    windowEl.style.top = `${top + (e.clientY - startY)}px`;
  });

  window.addEventListener("mouseup", () => {
    active = false;
  });
}

function bootScene() {
  const canvas = document.getElementById("scene") as HTMLCanvasElement;
  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
  });
  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 10);
  camera.position.z = 1;

  const geometry = new THREE.PlaneGeometry(2, 2, 64, 64);
  const material = new THREE.ShaderMaterial({
    uniforms: {
      t: { value: 0 },
      res: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) },
    },
    vertexShader: `
			varying vec2 vUv;
			void main() {
				vUv = uv;
				gl_Position = vec4(position, 1.0);
			}
		`,
    fragmentShader: `
			varying vec2 vUv;
			uniform float t;
			uniform vec2 res;
			float grid(vec2 uv, float size) {
				vec2 g = abs(fract(uv * size - 0.5) - 0.5) / fwidth(uv * size);
				return 1.0 - min(min(g.x, g.y), 1.0);
			}
			void main() {
				vec2 uv = vUv;
				float wave = sin((uv.y + t * 0.05) * 40.0) * 0.02;
				uv.x += wave;
				vec3 base = mix(vec3(0.02, 0.08, 0.16), vec3(0.06, 0.18, 0.34), uv.y);
				float g = grid(uv + vec2(t * 0.01, 0.0), 24.0) * 0.16;
				float scan = sin((uv.y + t * 0.2) * res.y * 0.04) * 0.03;
				vec3 color = base + vec3(0.1, 0.35, 0.45) * g + scan;
				gl_FragColor = vec4(color, 0.95);
			}
		`,
  });

  const quad = new THREE.Mesh(geometry, material);
  scene.add(quad);

  function resize() {
    renderer.setSize(window.innerWidth, window.innerHeight);
    material.uniforms.res.value.set(window.innerWidth, window.innerHeight);
  }

  function tick(time: number) {
    material.uniforms.t.value = time * 0.001;
    renderer.render(scene, camera);
    requestAnimationFrame(tick);
  }

  window.addEventListener("resize", resize);
  resize();
  requestAnimationFrame(tick);
}

async function api<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}

function normalize(path: string) {
  return path.replace(/\\/g, "/").replace(/^\/+/, "").toLowerCase();
}

function escapeHtml(input: string) {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
